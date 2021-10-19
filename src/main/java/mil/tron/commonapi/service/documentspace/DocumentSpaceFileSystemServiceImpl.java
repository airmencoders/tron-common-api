package mil.tron.commonapi.service.documentspace;

import com.amazonaws.services.s3.model.S3Object;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceFileSystemEntryRepository;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceRepository;
import mil.tron.commonapi.service.documentspace.util.FilePathSpec;
import mil.tron.commonapi.service.documentspace.util.FilePathSpecWithContents;
import mil.tron.commonapi.service.documentspace.util.FileSystemElementTree;
import mil.tron.commonapi.service.documentspace.util.S3ObjectAndFilename;
import org.apache.commons.io.FilenameUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DocumentSpaceFileSystemServiceImpl implements DocumentSpaceFileSystemService {
    private final DocumentSpaceRepository documentSpaceRepository;
    private final DocumentSpaceService documentSpaceService;
    private final DocumentSpaceFileSystemEntryRepository repository;
    public static final String PATH_SEP = "/";

    public DocumentSpaceFileSystemServiceImpl(DocumentSpaceRepository documentSpaceRepository,
                                              DocumentSpaceFileSystemEntryRepository repository,
                                              @Lazy DocumentSpaceService documentSpaceService) {
        this.documentSpaceRepository = documentSpaceRepository;
        this.repository = repository;
        this.documentSpaceService = documentSpaceService;
    }

    /**
     * Helper to convert a blank or null string into the root path ("/"),
     * or trim any whitespace off of a path
     * @param path the input path
     * @return output path to use
     */
    private String conditionPath(@Nullable String path) {
        if (path == null || path.isBlank() ||  path.trim().equals(PATH_SEP)) {
            return PATH_SEP;
        }
        return path.trim();
    }

    /**
     * Helper to verify a space UUID is real/exists
     * @param spaceId doc space UUID
     * @throws RecordNotFoundException if invalid doc space UUID
     */
    private void checkSpaceIsValid(UUID spaceId) {
        if (!documentSpaceRepository.existsById(spaceId)) {
            throw new RecordNotFoundException(String.format("Document Space %s not found", spaceId));
        }
    }

    /**
     * Utility to find out more information about a given path within a space.  The given space
     * is of a unix-like path relative to a doc space (prefix slash is optional) - e.g. /folder1/folder2
     * @param spaceId UUID of the space
     * @param path path to find out about
     * @return the FilePathSpec describing this path
     */
    @Override
    public FilePathSpec parsePathToFilePathSpec(UUID spaceId, @Nullable String path) {

        checkSpaceIsValid(spaceId);
        String lookupPath = conditionPath(path);

        String[] parts = lookupPath.split(PATH_SEP);
        UUID parentFolderId = UUID.fromString(DocumentSpaceFileSystemEntry.NIL_UUID);
        List<UUID> uuidList = new ArrayList<>();
        StringBuilder pathAccumulator = new StringBuilder();

        // dig into the path until last folder is found - this will be the parent folder
        //  all the while build out the full path leading up to this folder (names and uuids)
        //  to put for possible later use in the object's FilePathSpec object
        DocumentSpaceFileSystemEntry entry = null;
        if (parts.length != 0) {
            for (int i=0; i<parts.length; i++) {
                if (parts[i].isBlank()) continue;

                pathAccumulator.append(parts[i]).append(PATH_SEP);
                entry = repository
                        .findByDocumentSpaceIdEqualsAndItemNameEqualsAndParentEntryIdEquals(spaceId, parts[i], parentFolderId)
                        .orElseThrow(() -> new RecordNotFoundException(String.format("Path %s not found or is not a folder", pathAccumulator.toString())));

                if ((i+1) < parts.length) parentFolderId = entry.getItemId();  // update parent ID for the next depth iteration
                uuidList.add(entry.getItemId());
            }
        }

        return FilePathSpec.builder()
                .documentSpaceId(spaceId)
                .itemId(uuidList.isEmpty() ? UUID.fromString(DocumentSpaceFileSystemEntry.NIL_UUID) : uuidList.get(uuidList.size() - 1))
                .itemName(entry != null ? entry.getItemName() : "")
                .fullPathSpec(pathAccumulator.toString())
                .uuidList(uuidList)
                .parentFolderId(parentFolderId)
                .build();
    }

    @Override
    public FilePathSpecWithContents getFilesAndFoldersAtPath(UUID spaceId, @Nullable String path) {
        FilePathSpec spec = this.parsePathToFilePathSpec(spaceId, path);
        FilePathSpecWithContents contents = new DtoMapper().map(spec, FilePathSpecWithContents.class);
        List<S3Object> s3Objects = documentSpaceService.getAllFilesInFolder(spaceId, spec.getFullPathSpec());
        contents.setS3Objects(s3Objects);
        contents.setFiles(extractFilesFromPath(s3Objects.stream().map(S3Object::getKey).collect(Collectors.toList())));
        contents.setSubFolderElements(repository.findByDocumentSpaceIdEqualsAndParentEntryIdEqualsAndItemIdIsNot(
                spaceId,
                spec.getItemId(),
                spec.getUuidList().isEmpty()
                    ? UUID.fromString(DocumentSpaceFileSystemEntry.NIL_UUID)
                    : spec.getUuidList().get(spec.getUuidList().size() - 1)));

        return contents;
    }

    private List<String> extractFilesFromPath(List<String> paths) {
        return paths.stream()
                .map(item -> Path.of(item).getFileName().toString())
                .collect(Collectors.toList());
    }

    /**
     * Utility to take a raw file system entry entity and convert it to a FilePathSpec so that
     * we can know more information about it, etc
     * @param entity a DocumentSpaceFileSystemEntry object
     * @return the FilePathSpec object
     */
    @Override
    public FilePathSpec convertFileSystemEntityToFilePathSpec(DocumentSpaceFileSystemEntry entity) {

        checkSpaceIsValid(entity.getDocumentSpaceId());
        UUID parentFolderId = entity.getParentEntryId();
        List<UUID> uuidList = new ArrayList<>();
        StringBuilder pathAccumulator = new StringBuilder();
        pathAccumulator.append(entity.getItemName());
        uuidList.add(entity.getItemId());

        // traverse up in the tree to the root of the space (if we're not already at the root...)
        while (!parentFolderId.equals(UUID.fromString(DocumentSpaceFileSystemEntry.NIL_UUID))) {

            // get current item's parent entry from the db
            DocumentSpaceFileSystemEntry entry = repository
                    .findByDocumentSpaceIdEqualsAndItemIdEquals(entity.getDocumentSpaceId(), parentFolderId)
                    .orElseThrow(() -> new RecordNotFoundException(String.format("Parent folder %s not found", entity.getParentEntryId())));

            // build our UUID list and path leading up to the original entity as we traverse
            uuidList.add(0, entry.getItemId());
            pathAccumulator.insert(0, entry.getItemName() + PATH_SEP);
            parentFolderId = entry.getParentEntryId();
        }


        return FilePathSpec.builder()
                .itemId(entity.getItemId())
                .itemName(entity.getItemName())
                .documentSpaceId(entity.getDocumentSpaceId())
                .fullPathSpec(pathAccumulator.toString())
                .uuidList(uuidList)
                .parentFolderId(entity.getParentEntryId())
                .build();
    }

    /**
     * Gets list of DocumentSpaceFileSystemEntry elements (folders) within given space underneath given path (one-level-deep)
     * @param spaceId docu space UUID
     * @param path path to look under
     * @return list of DocumentSpaceFileSystemEntry elements (folders and files)
     */
    public List<DocumentSpaceFileSystemEntry> getElementsUnderneath(UUID spaceId, @Nullable String path) {
        checkSpaceIsValid(spaceId);
        String lookupPath = conditionPath(path);
        FilePathSpec spec = parsePathToFilePathSpec(spaceId, lookupPath);
        return repository.findByDocumentSpaceIdEqualsAndParentEntryIdEquals(spaceId, spec.getItemId());
    }

    /**
     * Dumps the element tree as a linked list of entries starting from "path" (e.g. "/" is root of space)
     * @param spaceId doc space UUID
     * @param path the path to dump from
     * @return linked list of DocumentSpaceFileSystemEntry objects
     */
    @Override
    public FileSystemElementTree dumpElementTree(UUID spaceId, @Nullable String path) {
        checkSpaceIsValid(spaceId);
        String lookupPath = conditionPath(path);
        FilePathSpec entry = parsePathToFilePathSpec(spaceId, lookupPath);
        DocumentSpaceFileSystemEntry element = repository.findByItemIdEquals(entry.getItemId()).orElseGet(() -> {
            if (entry.getItemId().equals(UUID.fromString(DocumentSpaceFileSystemEntry.NIL_UUID))) {
                // this is the root of the document space - so make a new element called "root" to base off of
                return DocumentSpaceFileSystemEntry.builder()
                        .documentSpaceId(spaceId)
                        .itemId(entry.getItemId())
                        .itemName(PATH_SEP)
                        .build();
            }
            else {
                 throw new RecordNotFoundException("Item ID not found");
            }
        });

        FileSystemElementTree tree = new FileSystemElementTree();
        tree.setValue(element);
        List<S3Object> files = documentSpaceService.getAllFilesInFolder(spaceId, lookupPath);
        tree.setFilePathSpec(entry);
        tree.setFiles(files);
        return buildTree(spaceId, element, tree);
    }

    /**
     * Helper for the dumpElementTree method - this is recursive method to build out the tree
     * @param spaceId space UUID
     * @param element the current DocumentSpaceFileSystemEntry
     * @param tree the tree we're building
     * @return FileSystemElementTree
     */
    private FileSystemElementTree buildTree(UUID spaceId, DocumentSpaceFileSystemEntry element, FileSystemElementTree tree) {

        List<DocumentSpaceFileSystemEntry> children = repository.findByDocumentSpaceIdEqualsAndParentEntryIdEquals(spaceId, element.getItemId());
        if (children.isEmpty()) {
            return tree;  // no children under this element
        }

        if (tree.getNodes() == null) tree.setNodes(new ArrayList<>());
        for (DocumentSpaceFileSystemEntry entry : children) {
            FileSystemElementTree subTree = buildTree(spaceId, entry, new FileSystemElementTree());
            subTree.setValue(entry);
            FilePathSpec spec = convertFileSystemEntityToFilePathSpec(entry);
            List<S3Object> files = documentSpaceService.getAllFilesInFolder(spaceId, spec.getFullPathSpec());
            subTree.setFilePathSpec(spec);
            subTree.setFiles(files);
            tree.addNode(subTree);
        }

        return tree;
    }

    /**
     * Attempts to add a folder entry to given document space underneath given parent folder name
     * @param spaceId docu space UUID
     * @param name name of the new folder
     * @param path name of parent folder to nest under - null if to place at root level of space
     * @return the created doc space folder/filesystem entry
     * @throws RecordNotFoundException for invalid space UUID
     * @throws ResourceAlreadyExistsException for folder of that name already under given parent within space
     */
    @Override
    public DocumentSpaceFileSystemEntry addFolder(UUID spaceId, String name, @Nullable String path) {

        checkSpaceIsValid(spaceId);

        String lookupPath = conditionPath(path);
        FilePathSpec pathSpec = parsePathToFilePathSpec(spaceId, lookupPath);

        // check no existence of duplicate -- so we don't get a nasty DB exception
        if (repository.existsByDocumentSpaceIdAndParentEntryIdAndItemName(spaceId, pathSpec.getItemId(), name)) {
            throw new ResourceAlreadyExistsException(String.format("Folder with name %s already exists under that parent", name));
        }

        return repository.save(DocumentSpaceFileSystemEntry.builder()
                .documentSpaceId(spaceId)
                .parentEntryId(pathSpec.getItemId())
                .itemId(UUID.randomUUID()) // assign UUID to it (gets auto assigned anyways if omitted)
                .itemName(name)
                .build());
    }

    /**
     * Sees the given item name inside given path is a folder or not
     * @param spaceId doc space id
     * @param path the path holding "item name"
     * @param itemName the item name
     * @return true if directory else false
     */
    public boolean isFolder(UUID spaceId, String path, String itemName) {
        UUID parentFolderItemId = parsePathToFilePathSpec(spaceId, path).getItemId();
        return repository.existsByDocumentSpaceIdAndParentEntryIdAndItemName(spaceId,
                parentFolderItemId,
                FilenameUtils.normalizeNoEndSeparator(itemName));
    }

    /**
     * Deletes a folder and all that is contained within/underneath it (like a `rm -rf` in Unix)
     * @param spaceId doc space UUID
     * @param path path to the folder to delete
     */
    @Override
    public void deleteFolder(UUID spaceId, String path) {
        UUID lastIdToDelete = parsePathToFilePathSpec(spaceId, path).getItemId();  // this is the last element to delete
                                                                                    // which is the 'root' of the given path
        deleteParentDirectories(dumpElementTree(spaceId, path));
        repository.deleteByDocumentSpaceIdEqualsAndItemIdEquals(spaceId, lastIdToDelete);
    }

    /**
     * Private helper for the deleteFolder method to dig in depth first to a file structure and delete
     * the files and folders on the way up
     * @param tree the current tree we're on
     */
    private void deleteParentDirectories(FileSystemElementTree tree) {

        // walk the tree depth-first and delete on the way up
        if (tree.getNodes() == null || tree.getNodes().isEmpty()) {
            // delete files before the folder
            for (S3Object obj : tree.getFiles()) {
                documentSpaceService.deleteS3ObjectByKey(obj.getKey());
            }
            repository.deleteByDocumentSpaceIdEqualsAndItemIdEquals(tree.getValue().getDocumentSpaceId(), tree.getValue().getItemId());
            return;
        }

        for (FileSystemElementTree node : tree.getNodes()) {
            deleteParentDirectories(node);
        }
    }

    @Override
    public List<S3ObjectAndFilename> flattenTreeToS3ObjectAndFilenameList(FileSystemElementTree tree) {
        List<S3ObjectAndFilename> retList = new ArrayList<>();
        scrapeFolders(tree, retList);
        return retList;
    }

    /**
     * Private helper to assist the the flatten tree method in digging into a file structure
     * @param tree the current tree we're scraping
     * @param items the cumulative list of item paths
     */
    private void scrapeFolders(FileSystemElementTree tree, List<S3ObjectAndFilename> items) {
        for (S3Object obj : tree.getFiles()) {
            items.add(S3ObjectAndFilename.builder()
                    .pathAndFilename(joinPathParts(tree.getFilePathSpec().getFullPathSpec(), FilenameUtils.getName(obj.getKey())))
                    .s3Object(obj)
                    .build());
        }

        if (tree.getNodes() == null || tree.getNodes().isEmpty()) {
            return;
        }

        for (FileSystemElementTree node : tree.getNodes()) {
            scrapeFolders(node, items);
        }

    }

    /**
     * Helper to form a path of components - making sure we start and end with "/" but removing duplicate
     * sequences of "//" with single "/"
     * @param parts
     * @return
     */
    private String joinPathParts(String... parts) {
        return (PATH_SEP + String.join(PATH_SEP, parts)).replace(PATH_SEP + PATH_SEP, PATH_SEP);
    }


    /**
     * Renames a folder indicated by its existing path
     * @param spaceId doc space UUID
     * @param existingPath the existing path + the folder name (e.g. /docs/lists)
     * @param newFolderName the new name (e.g. new-lists)
     */
    public void renameFolder(UUID spaceId, String existingPath, String newFolderName) {
        checkSpaceIsValid(spaceId);
        FilePathSpec spec = this.parsePathToFilePathSpec(spaceId, existingPath);
        DocumentSpaceFileSystemEntry entry = repository.findByItemIdEquals(spec.getItemId())
                .orElseThrow(() -> new RecordNotFoundException("Cannot find existing element with UUID " + spec.getItemId()));

        // make sure we don't have a same named folder at this path before allowing a name change
        Optional<DocumentSpaceFileSystemEntry> existingEntry =
                repository.findByDocumentSpaceIdEqualsAndItemNameEqualsAndParentEntryIdEquals(spaceId, newFolderName, spec.getParentFolderId());

        if (existingEntry.isEmpty()) {
            // allow the name change
            entry.setItemName(newFolderName);
            repository.save(entry);
        }
        else {
            throw new ResourceAlreadyExistsException("A folder with that name already exists at this level");
        }
    }
}

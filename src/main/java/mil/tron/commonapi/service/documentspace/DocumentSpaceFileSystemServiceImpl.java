package mil.tron.commonapi.service.documentspace;

import com.amazonaws.services.s3.model.S3Object;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceFileSystemEntryRepository;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.util.*;

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

    private void checkSpaceIsValid(UUID spaceId) {
        if (!documentSpaceRepository.existsById(spaceId)) {
            throw new RecordNotFoundException(String.format("Document Space %s not found", spaceId));
        }
    }

    /**
     * Utility to find out more information about a given path within a space.
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
        if (parts.length != 0) {
            for (String folder : parts) {
                if (folder.isBlank()) continue;

                pathAccumulator.append(folder).append(PATH_SEP);
                DocumentSpaceFileSystemEntry entry = repository
                        .findByDocumentSpaceIdEqualsAndItemNameEqualsAndParentEntryIdEquals(spaceId, folder, parentFolderId)
                        .orElseThrow(() -> new RecordNotFoundException(String.format("Path %s not found", pathAccumulator.toString())));

                parentFolderId = entry.getItemId();  // update parent ID for the next depth iteration
                uuidList.add(entry.getItemId());
            }
        }

        return FilePathSpec.builder()
                .documentSpaceId(spaceId)
                .itemId(uuidList.isEmpty() ? UUID.fromString(DocumentSpaceFileSystemEntry.NIL_UUID) : uuidList.get(uuidList.size() - 1))
                .fullPathSpec(pathAccumulator.toString())
                .uuidList(uuidList)
                .parentFolderId(parentFolderId)
                .build();
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
     * Gets list of DocumentSpaceFileSystemEntry elements within given space underneath given path (one-level-deep)
     * @param spaceId docu space UUID
     * @param path path to look under
     * @return list of DocumentSpaceFileSystemEntry elements
     */
    public List<DocumentSpaceFileSystemEntry> getElementsUnderneath(UUID spaceId, @Nullable String path) {

        checkSpaceIsValid(spaceId);

        String lookupPath = conditionPath(path);
        FilePathSpec spec = parsePathToFilePathSpec(spaceId, lookupPath);
        return repository.findByDocumentSpaceIdEqualsAndParentEntryIdEquals(spaceId, spec.getParentFolderId());
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
        FilePathSpec entry = parsePathToFilePathSpec(spaceId, conditionPath(path));
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
        if (repository.existsByDocumentSpaceIdAndParentEntryIdAndItemName(spaceId, pathSpec.getParentFolderId(), name)) {
            throw new ResourceAlreadyExistsException(String.format("Folder with name %s already exists under that parent", name));
        }

        return repository.save(DocumentSpaceFileSystemEntry.builder()
                .documentSpaceId(spaceId)
                .parentEntryId(pathSpec.getParentFolderId())
                .itemId(UUID.randomUUID()) // assign UUID to it (gets auto assigned anyways if omitted)
                .itemName(name)
                .build());
    }

    /**
     * Deletes a folder and all that is contained within/underneath it (like a `rm -rf` in Unix)
     * @param spaceId doc space UUID
     * @param path path to the folder to delete
     */
    @Override
    public void deleteFolder(UUID spaceId, String path) {
        checkSpaceIsValid(spaceId);
        deleteParentDirectories(dumpElementTree(spaceId, path));
        repository.deleteByDocumentSpaceIdEqualsAndItemIdEquals(spaceId, parsePathToFilePathSpec(spaceId, path).getItemId());
    }

    private void deleteParentDirectories(FileSystemElementTree tree) {

        // walk the tree depth-first and delete on the way up
        if (tree.getNodes() == null || tree.getNodes().isEmpty()) {
            repository.deleteByDocumentSpaceIdEqualsAndItemIdEquals(tree.getValue().getDocumentSpaceId(), tree.getValue().getItemId());
            return;
        }

        for (FileSystemElementTree node : tree.getNodes()) {
            deleteParentDirectories(node);
        }
    }
}

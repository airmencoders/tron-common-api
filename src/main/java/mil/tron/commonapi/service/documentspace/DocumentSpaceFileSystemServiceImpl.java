package mil.tron.commonapi.service.documentspace;

import com.amazonaws.services.s3.model.MultiObjectDeleteException.DeleteError;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import mil.tron.commonapi.dto.documentspace.DocumentDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceFolderInfoDto;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.exception.documentspace.FolderDepthException;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceFileSystemEntryRepository;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceRepository;
import mil.tron.commonapi.service.documentspace.util.FilePathSpec;
import mil.tron.commonapi.service.documentspace.util.FilePathSpecWithContents;
import mil.tron.commonapi.service.documentspace.util.FileSystemElementTree;
import mil.tron.commonapi.service.documentspace.util.S3ObjectAndFilename;
import mil.tron.commonapi.validations.DocSpaceFolderOrFilenameValidator;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry.NIL_UUID;

@Service
public class DocumentSpaceFileSystemServiceImpl implements DocumentSpaceFileSystemService {
    private final DocumentSpaceRepository documentSpaceRepository;
    private final DocumentSpaceService documentSpaceService;
    private final DocumentSpaceFileSystemEntryRepository repository;
    private final DocumentSpaceFileService documentSpaceFileService;
    private final DocumentSpaceUserCollectionService documentSpaceUserCollectionService;
    public static final String PATH_SEP = "/";
    private static final String BAD_PATH = "Path %s not found or is not a folder";
    protected static final int MAX_FOLDER_DEPTH = 20;

    public DocumentSpaceFileSystemServiceImpl(DocumentSpaceRepository documentSpaceRepository,
                                              DocumentSpaceFileSystemEntryRepository repository,
                                              @Lazy DocumentSpaceService documentSpaceService,
                                              DocumentSpaceFileService documentSpaceFileService, DocumentSpaceUserCollectionService documentSpaceUserCollectionService) {
        this.documentSpaceRepository = documentSpaceRepository;
        this.repository = repository;
        this.documentSpaceService = documentSpaceService;
        this.documentSpaceFileService = documentSpaceFileService;
        this.documentSpaceUserCollectionService = documentSpaceUserCollectionService;
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

    @Override
    public FilePathSpec parsePathToFilePathSpec(UUID spaceId, @Nullable String path) {
        return this.parsePathToFilePathSpec(spaceId, path, false);
    }

    /**
     * Utility to find out more information about a given path within a space. The
     * given space is of a unix-like path relative to a doc space (prefix slash is
     * optional) - e.g. /folder1/folder2
     * 
     * @param spaceId UUID of the space
     * @param path    path to find out about
     * @param createFolders true to create folders along the path if they do not exist
     * @return the FilePathSpec describing this path
     */
    @Override
    public FilePathSpec parsePathToFilePathSpec(UUID spaceId, @Nullable String path, boolean createFolders) {

        checkSpaceIsValid(spaceId);
        String lookupPath = conditionPath(path);

        UUID parentFolderId = NIL_UUID;
        List<UUID> uuidList = new ArrayList<>();
        StringBuilder pathAccumulator = new StringBuilder();

        // dig into the path until last folder is found - this will be the parent folder
        //  all the while build out the full path leading up to this folder (names and uuids)
        //  to put for possible later use in the object's FilePathSpec object
        DocumentSpaceFileSystemEntry entry = null;
        
        Path asPath = Paths.get(lookupPath);

        // if we're allowed to create folders on this call, then
        // first check we won't exceed max folder depth
        if (createFolders && countPathDepth(lookupPath) > MAX_FOLDER_DEPTH) {
            throw new FolderDepthException("Requested path exceeded the MAX FOLDER DEPTH of " + MAX_FOLDER_DEPTH);
        }

        for (Iterator<Path> currentPathItem = asPath.iterator(); currentPathItem.hasNext();) {
            String currentPathItemAsString = currentPathItem.next().toString();

            pathAccumulator.append(currentPathItemAsString).append(PATH_SEP);

            Optional<DocumentSpaceFileSystemEntry> possibleEntry = repository
                    .findByDocumentSpaceIdEqualsAndItemNameEqualsAndParentEntryIdEquals(spaceId,
                            currentPathItemAsString, parentFolderId);

            if (createFolders && possibleEntry.isEmpty()) {
                // we did want to create missing folders, and the entry didn't exist, then create it (after validation)
                DocSpaceFolderOrFilenameValidator validator = new DocSpaceFolderOrFilenameValidator();
                if (!validator.isValid(currentPathItemAsString, null)) {
                    throw new BadRequestException(String.format("Invalid folder name - %s", currentPathItemAsString));
                }

                DocumentSpaceFileSystemEntry newEntry = DocumentSpaceFileSystemEntry.builder()
                        .isFolder(true)
                        .parentEntryId(parentFolderId)
                        .documentSpaceId(spaceId)
                        .itemName(currentPathItemAsString)
                        .etag(createFolderETag(spaceId, parentFolderId, currentPathItemAsString))
                        .build();

                entry = repository.save(newEntry);
            }
            else if (possibleEntry.isPresent()) {
                // the element is present, so who cares about createFolder flag, just unwrap the element and proceed
                entry = possibleEntry.get();
            }
            else {
                // we get here, should mean that we didn't want to create folders, and the element didn't exist so throw
                throw new RecordNotFoundException(String.format(BAD_PATH, pathAccumulator.toString()));
            }

            if (currentPathItem.hasNext()) parentFolderId = entry.getItemId();  // update parent ID for the next depth iteration
            uuidList.add(entry.getItemId());
        }

        return FilePathSpec.builder()
                .documentSpaceId(spaceId)
                .itemId(uuidList.isEmpty() ? NIL_UUID : uuidList.get(uuidList.size() - 1))
                .itemName(entry != null ? entry.getItemName() : "")
                .fullPathSpec(pathAccumulator.toString())
                .uuidList(uuidList)
                .parentFolderId(parentFolderId)
                .build();
    }

    @Override
    public FilePathSpecWithContents getFilesAndFoldersAtPath(UUID spaceId, @Nullable String path) {
        FilePathSpec spec = this.parsePathToFilePathSpec(spaceId, path);
        
        // Prevent dumping contents at a folder that is archived
        Optional<DocumentSpaceFileSystemEntry> folder = repository.findByItemIdEquals(spec.getItemId());
        folder.ifPresent(item -> {
        	if (item.isDeleteArchived()) {
        		throw new RecordNotFoundException("Cannot dump contents of an archived folder");
        	}
        });
        
        FilePathSpecWithContents contents = new DtoMapper().map(spec, FilePathSpecWithContents.class);
        contents.setEntries(repository.findByDocumentSpaceIdEqualsAndParentEntryIdEqualsAndIsDeleteArchivedEquals(spaceId, spec.getItemId(), false));

        contents.getEntries().forEach(entry -> 
            entry.setHasNonArchivedContents(repository.existsByParentEntryIdAndIsDeleteArchivedFalse(entry.getItemId()))
        );
        return contents;
    }

    @Override
    public List<DocumentDto> getArchivedItems(UUID spaceId) {
        List<DocumentDto> elements = new ArrayList<>();

        checkSpaceIsValid(spaceId);
        FilePathSpec entry = parsePathToFilePathSpec(spaceId, PATH_SEP);
        DocumentSpaceFileSystemEntry element = repository.findByItemIdEquals(entry.getItemId()).orElseGet(() -> {
            if (entry.getItemId().equals(NIL_UUID)) {
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

        return walkTreeForArchivedItems(spaceId, element, elements, PATH_SEP);
    }

    private List<DocumentDto> walkTreeForArchivedItems(UUID spaceId,
                                                        DocumentSpaceFileSystemEntry element,
                                                        List<DocumentDto> elements,
                                                        String path) {

        List<DocumentSpaceFileSystemEntry> children = repository.findByDocumentSpaceIdEqualsAndParentEntryIdEquals(spaceId, element.getItemId());
        for (DocumentSpaceFileSystemEntry child : children) {
            if (child.isDeleteArchived()) {
                elements.add(DocumentDto.builder()
                    .isFolder(child.isFolder())
                    .size(child.getSize())
                    .spaceId(spaceId.toString())
                    .path(path)
                    .lastModifiedDate(child.getLastModifiedOn())
                    .lastModifiedBy(child.getLastModifiedBy())
                    .key(child.getItemName())
                    .build());
            } else if (child.isFolder()) {
                elements.addAll(walkTreeForArchivedItems(spaceId, child, new ArrayList<>(), joinPathParts(path, child.getItemName())));
            }
        }

        return elements;
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
        while (!parentFolderId.equals(NIL_UUID)) {

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
    public FileSystemElementTree dumpElementTree(UUID spaceId, @Nullable String path, boolean includeArchived) {
    	checkSpaceIsValid(spaceId);
        String lookupPath = conditionPath(path);
        FilePathSpec entry = parsePathToFilePathSpec(spaceId, lookupPath);
        DocumentSpaceFileSystemEntry element = repository.findByItemIdEquals(entry.getItemId()).orElseGet(() -> {
            if (entry.getItemId().equals(NIL_UUID)) {
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

        return this.dumpElementTreeFromElementEntry(element, entry, lookupPath, includeArchived);
    }

    private FileSystemElementTree dumpElementTreeFromElementEntry(DocumentSpaceFileSystemEntry element, FilePathSpec entry, String lookupPath, boolean includeArchived) {
        FileSystemElementTree tree = new FileSystemElementTree();
        tree.setValue(element);
        List<S3ObjectSummary> files = documentSpaceService.getAllFilesInFolder(element.getDocumentSpaceId(), lookupPath, includeArchived);
        tree.setFilePathSpec(entry);
        tree.setFiles(files);
        return buildTree(element.getDocumentSpaceId(), element, tree, includeArchived);
    }

    /**
     * Helper for the dumpElementTree method - this is recursive method to build out the tree
     * @param spaceId space UUID
     * @param element the current DocumentSpaceFileSystemEntry
     * @param tree the tree we're building
     * @param includeArchived true if archived files should be included
     * @return FileSystemElementTree
     */
    private FileSystemElementTree buildTree(UUID spaceId, DocumentSpaceFileSystemEntry element, FileSystemElementTree tree, boolean includeArchived) {

        List<DocumentSpaceFileSystemEntry> children = repository.findByDocumentSpaceIdEqualsAndParentEntryIdEqualsAndIsFolderTrue(spaceId, element.getItemId());
        if (children.isEmpty()) {
            return tree;  // no children under this element
        }

        if (tree.getNodes() == null) tree.setNodes(new ArrayList<>());
        for (DocumentSpaceFileSystemEntry entry : children) {
            FileSystemElementTree subTree = buildTree(spaceId, entry, new FileSystemElementTree(), includeArchived);
            subTree.setValue(entry);
            FilePathSpec spec = convertFileSystemEntityToFilePathSpec(entry);
            List<S3ObjectSummary> files = documentSpaceService.getAllFilesInFolder(spaceId, spec.getFullPathSpec(), includeArchived);
            subTree.setFilePathSpec(spec);
            subTree.setFiles(files);
            tree.addNode(subTree);
        }

        return tree;
    }

    /**
     * Attempts to add a folder entry to given document space underneath given parent folder name.
     * Note that creation of folders in an archived state is not allowed.
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
        
        // Check if adding this folder would violate the max folder depth
        if (pathSpec.getUuidList().size() + 1 > MAX_FOLDER_DEPTH) {
        	throw new FolderDepthException(MAX_FOLDER_DEPTH);
        }

        // check no existence of duplicate ... for now we're not even allowing an archived variant to have same name/path
        //  the db schema technically supports it but for sake of simplicity we disallow for now
        if (repository.existsByDocumentSpaceIdAndParentEntryIdAndItemName(spaceId, pathSpec.getItemId(), name)) {
            throw new ResourceAlreadyExistsException(String.format("File or folder with name %s already exists here or is archived under that same path/name", name));
        }

        DocumentSpaceFileSystemEntry savedFolder = repository.save(DocumentSpaceFileSystemEntry.builder()
                .documentSpaceId(spaceId)
                .parentEntryId(pathSpec.getItemId())
                .itemId(UUID.randomUUID()) // assign UUID to it (gets auto assigned anyways if omitted)
                .itemName(name)
                .isFolder(true)
                .etag(createFolderETag(spaceId, pathSpec.getParentFolderId(), name))
                .build());
        
        propagateModificationStateToAncestors(savedFolder);
        
        return savedFolder;
    }

    /**
     * Sees the given item name inside given path is a folder or not
     * @param spaceId doc space id
     * @param path the path holding "item name"
     * @param itemName the item name
     * @return true if directory else false
     */
    @Override
    public boolean isFolder(UUID spaceId, String path, String itemName) {
        UUID parentFolderItemId = parsePathToFilePathSpec(spaceId, path).getItemId();
        return repository.existsByDocumentSpaceIdAndParentEntryIdAndItemNameAndIsFolderTrue(spaceId,
                parentFolderItemId,
                FilenameUtils.normalizeNoEndSeparator(itemName));
    }

    /**
     * Helper to see if a particular S3 item is considered archived according to our database
     * @param spaceId doc space id
     * @param parentId id of the file system element that is the part of said itemName
     * @param itemName the file name itself
     * @return true if archived
     */
    @Override
    public boolean isArchived(UUID spaceId, UUID parentId, String itemName) {
        Optional<DocumentSpaceFileSystemEntry> entry =
                repository.findByDocumentSpaceIdEqualsAndItemNameEqualsAndParentEntryIdEqualsAndIsDeleteArchivedEquals(spaceId, itemName, parentId, true);

        return entry.isPresent();
    }

    /**
     * Deletes a folder and all that is contained within/underneath it (like a `rm -rf` in Unix)
     * @param spaceId doc space UUID
     * @param path path to the folder to delete
     */
    @Override
    public void deleteFolder(UUID spaceId, String path) {
        FileSystemElementTree tree = dumpElementTree(spaceId, path, true);
        deleteParentDirectories(tree);
        
        propagateModificationStateToAncestors(tree.getValue());
    }

    /**
     * Private helper for the deleteFolder method to dig in depth first to a file structure and delete
     * the files and folders on the way up
     * @param tree the current tree we're on
     */
    private void deleteParentDirectories(FileSystemElementTree tree) {
        // walk the tree depth-first and delete on the way up
        if (tree.getNodes() == null || tree.getNodes().isEmpty()) {
        	purgeDirectory(tree);
            return;
        }

        for (FileSystemElementTree node : tree.getNodes()) {
            deleteParentDirectories(node);
        }
        
        purgeDirectory(tree);
    }
    
    private void purgeDirectory(FileSystemElementTree tree) {
    	// delete files before the folder
    	String[] keysToDelete = tree.getFiles().stream().map(S3ObjectSummary::getKey).toArray(String[]::new);
        List<DeleteError> errors = keysToDelete.length > 0 ? documentSpaceService.deleteS3ObjectsByKey(keysToDelete) : new ArrayList<>();
        
		/*
		 * Conditionally delete files from the database. If there were no error, then it
		 * is safe to delete all files and the parent folder. Any file that received an
		 * error from being deleted from S3 will be checked. Do not delete any file from
		 * the database that could not be deleted from S3 except for S3 NoSuchKey errors.
		 */
		if (errors.isEmpty()) {
			documentSpaceFileService.deleteAllDocumentSpaceFilesInParentFolder(
					tree.getFilePathSpec().getDocumentSpaceId(), tree.getValue().getItemId());

			repository.deleteByDocumentSpaceIdEqualsAndItemIdEquals(tree.getValue().getDocumentSpaceId(),
					tree.getValue().getItemId());
			repository.flush();
		} else {
			documentSpaceFileService.deleteAllDocumentSpaceFilesInParentFolderExcept(
					tree.getFilePathSpec().getDocumentSpaceId(), tree.getValue().getItemId(),
					new HashSet<>(
							extractFilesFromPath(errors.stream().filter(error -> !"NoSuchKey".equals(error.getCode()))
									.map(DeleteError::getKey).collect(Collectors.toList()))));
		}
    }

    /**
     * Archives or un-archives a file or folder and all its elements (folders and files)
     * @param spaceId doc space UUID
     * @param path the path (including folder name if archiving a folder)
     * @param itemName element to archive
     */
    @Override
    public void archiveElement(UUID spaceId, String path, String itemName) {

        FilePathSpec owningFolderEntry = parsePathToFilePathSpec(spaceId, path);

        // refuse to archive root folder
        if (itemName.equals(PATH_SEP) || itemName.isBlank()) {
            throw new BadRequestException("Cannot archive the root folder");
        }

        // check for existence of same folder/path/name that's already of the desired status
        if (repository.existsByDocumentSpaceIdAndParentEntryIdAndItemNameAndIsDeleteArchivedEquals(spaceId,
                owningFolderEntry.getItemId(),
                itemName,
                true)) {

            throw new ResourceAlreadyExistsException("A folder or file with that path and name is already archived");
        }

        DocumentSpaceFileSystemEntry startEntry = repository.findByDocumentSpaceIdEqualsAndItemNameEqualsAndParentEntryIdEquals(spaceId, itemName, owningFolderEntry.getItemId())
                .orElseThrow(() -> new RecordNotFoundException("Unable to get item for archive"));

        documentSpaceUserCollectionService.removeEntityFromAllCollections(startEntry.getId());
        archiveOrUnarchiveChildren(spaceId, startEntry, true);
        propagateModificationStateToAncestors(startEntry);
    }

    @Override
    public void unArchiveElements(UUID spaceId, List<String> items) {

        // get list of what's really in archived - according to the database
        List<DocumentDto> archivedItems = this.getArchivedItems(spaceId);

        // make sure the requested items to restore start with a PATH_SEP
        List<String> itemsRequested = items.stream()
                .map(item -> item.startsWith(PATH_SEP) ? item : (PATH_SEP + item))
                .collect(Collectors.toList());

        // of those items, only take the ones that were given by user (this vets for validity as well for us)
        List<DocumentDto> itemsToUnArchive = archivedItems.stream()
                .filter(item -> itemsRequested.contains(joinPathParts(item.getPath(), item.getKey())))
                .collect(Collectors.toList());

        for (DocumentDto item : itemsToUnArchive) {

            FilePathSpec owningElement = parsePathToFilePathSpec(spaceId, item.getPath());

            // if parent is not ROOT..
            if (!owningElement.getItemId().equals(NIL_UUID)) {
                // refuse to unarchive if its parent is archived (like mac os)
                DocumentSpaceFileSystemEntry parentElem = repository.findByItemIdEquals(owningElement.getItemId())
                        .orElseThrow(() -> new RecordNotFoundException("Cannot find that parent folder record"));

                if (parentElem.isDeleteArchived()) {
                    throw new BadRequestException("Cannot unarchive an item if its parent is archived");
                }
            }

            // if we get here - either parent is ROOT or its parent is NOT archived, so its valid for un-archiving
            DocumentSpaceFileSystemEntry startEntry = repository.findByDocumentSpaceIdEqualsAndItemNameEqualsAndParentEntryIdEquals(spaceId, item.getKey(), owningElement.getItemId())
                    .orElseThrow(() -> new RecordNotFoundException("Unable to get item for un-archive"));
            archiveOrUnarchiveChildren(spaceId, startEntry, false);
            propagateModificationStateToAncestors(startEntry);
        }
    }


    private void archiveOrUnarchiveChildren(UUID spaceId, DocumentSpaceFileSystemEntry entry, boolean doArchive) {

        List<DocumentSpaceFileSystemEntry> children = repository.findByDocumentSpaceIdEqualsAndParentEntryIdEquals(spaceId, entry.getItemId());
        if (children == null || children.isEmpty()) {
            entry.setDeleteArchived(doArchive);
            repository.save(entry);
            return;
        }

        for (DocumentSpaceFileSystemEntry e : children) {
            archiveOrUnarchiveChildren(spaceId, e, doArchive);
        }

        entry.setDeleteArchived(doArchive);
        repository.save(entry);
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
        for (S3ObjectSummary obj : tree.getFiles()) {
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
    public static String joinPathParts(String... parts) {
        return (PATH_SEP + String.join(PATH_SEP, parts)).replaceAll("/+", PATH_SEP);
    }

    /**
     * Helper to count the depth of a given path for depth checks
     * @param path the path to analyze
     * @return count of path segments
     */
    public static int countPathDepth(@Nullable String path) {

        if (path == null) return 0;

        // by first splitting it and passing it through the path parts joiner
        //  we ensure it starts with a PATH_SEP, has no dupe PATH_SEPS, and doesn't
        //  end with a PATH_SEP so that way we dont get incorrect count
        return removeTrailingSlashes(joinPathParts(path.split(PATH_SEP))).split(PATH_SEP).length - 1;
    }

    public static String removeTrailingSlashes(@Nullable String input) {
        if (input == null) return "";

        int index = input.length() - 1;
        while (index >= 0) {
            if (input.charAt(index) == '/') index--;
            else break;
        }

        return input.substring(0, index+1);
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
            entry.setEtag(createFolderETag(entry.getDocumentSpaceId(), entry.getParentEntryId(), newFolderName));
            repository.save(entry);
            propagateModificationStateToAncestors(entry);
        }
        else {
            throw new ResourceAlreadyExistsException("A folder with that name already exists at this level");
        }
    }
    
    private String createFolderETag(UUID documentSpaceId, UUID parentFolderId, String folderName) {
    	return DigestUtils.md5Hex(String.format("%s/%s/%s", documentSpaceId.toString(), parentFolderId.toString(), folderName));
    }

    @Override
    public void saveItem(DocumentSpaceFileSystemEntry entry) {
        repository.saveAndFlush(entry);
    }

    @Override
    public DocumentSpaceFileSystemEntry getElementByItemId(UUID itemId) {
        return repository.findByItemIdEquals(itemId).orElseThrow(() -> new RecordNotFoundException("No item with that Item Id Exists"));
    }

    @Override
    public Optional<DocumentSpaceFileSystemEntry> getByParentIdAndItemName(UUID spaceId, UUID parentId, String itemName) {
        return repository.findByDocumentSpaceIdEqualsAndItemNameEqualsAndParentEntryIdEquals(spaceId, itemName, parentId);
    }

    /**
     * For cross space moves, we need to go thru each element and change the document space ID... just changing its parent
     * earlier does no good... we can end up with orphaned entries
     * @param destinationSpaceId
     * @param startingEntry
     * @param newParentId
     */
    @Override
    public void moveFileSystemEntryTree(UUID destinationSpaceId, DocumentSpaceFileSystemEntry startingEntry, UUID newParentId) {

        // we don't need to do this logic if moving within same document space
        if (destinationSpaceId.equals(startingEntry.getDocumentSpaceId())) return;

        // see if this entry contains children (if its a parent to anyone)
        List<DocumentSpaceFileSystemEntry> children = repository
                .findByDocumentSpaceIdEqualsAndParentEntryIdEquals(startingEntry.getDocumentSpaceId(), startingEntry.getItemId());

        if (children.isEmpty()) return;

        for (DocumentSpaceFileSystemEntry child : children) {
            child.setDocumentSpaceId(destinationSpaceId);
            child = repository.saveAndFlush(child);
            duplicateFileSystemEntryTree(destinationSpaceId, child, child.getItemId()); // recurse
        }
    }

    /**
     * Copies the contents contained underneath the given file system entry (if it contains children).  This is
     * used for folder copy operations before we make the actual copies of the physical files pointed to within S3.
     * @param destinationSpaceId what space ID we're copying to
     * @param startingEntry the file system entry to look at, see if it contains children, and then recurse into copying each
     *                      entry to its own, new entry
     * @param newParentId the new parent (changes on each re-cursed call) to assign the current child
     */
    @Override
    public void duplicateFileSystemEntryTree(UUID destinationSpaceId, DocumentSpaceFileSystemEntry startingEntry, UUID newParentId) {

        // see if this entry contains children (if its a parent to anyone)
        List<DocumentSpaceFileSystemEntry> children = repository
                .findByDocumentSpaceIdEqualsAndParentEntryIdEquals(startingEntry.getDocumentSpaceId(), startingEntry.getItemId());

        if (children.isEmpty()) return;

        for (DocumentSpaceFileSystemEntry child : children) {
            DocumentSpaceFileSystemEntry childCopy = DocumentSpaceFileSystemEntry.builder()
                    .id(UUID.randomUUID())
                    .isFolder(child.isFolder())
                    .isDeleteArchived(child.isDeleteArchived())
                    .lastModifiedOn(child.getLastModifiedOn())
                    .lastModifiedBy(child.getLastModifiedBy())
                    .parentEntryId(newParentId)
                    .itemId(UUID.randomUUID())
                    .hasNonArchivedContents(child.isHasNonArchivedContents())
                    .size(child.getSize())
                    .documentSpaceId(destinationSpaceId)
                    .createdBy(child.getCreatedBy())
                    .createdOn(child.getCreatedOn())
                    .etag(child.getEtag())
                    .itemName(child.getItemName())
                    .build();

            childCopy = repository.saveAndFlush(childCopy);
            duplicateFileSystemEntryTree(destinationSpaceId, childCopy, childCopy.getItemId()); // recurse
        }
    }

    /**
     * For each file added or updated, go back up its ancestry and update (possibly) the last modified date of the folders
     * @param propagateFrom file system entry to start from
     * @return all the updated ancestors
     */
	@Override
	public List<DocumentSpaceFileSystemEntry> propagateModificationStateToAncestors(DocumentSpaceFileSystemEntry propagateFrom) {
		Deque<DocumentSpaceFileSystemEntry> entitiesToPropagateTo = null;
		Date lastModifiedDate = repository.findMostRecentModifiedDateAmongstSiblings(propagateFrom.getDocumentSpaceId(), propagateFrom.getParentEntryId())
                .orElse(new Date());
		
		try {
			entitiesToPropagateTo = getAncestorHierarchy(propagateFrom);
		} catch (RecordNotFoundException ex) {
			entitiesToPropagateTo = new ArrayDeque<>();
		}
		
		if (entitiesToPropagateTo.isEmpty()) {
			return new ArrayList<>();
		}

		// only propagate up to ancestors with an older last mod date
		List<DocumentSpaceFileSystemEntry> updatedEntities = entitiesToPropagateTo.stream()
            .filter(entity -> {
                if (entity.getLastModifiedOn() == null) {
                    return true;
                }
                return lastModifiedDate.getTime() > entity.getLastModifiedOn().getTime();
            })
            .collect(Collectors.toList());

		updatedEntities.forEach(entity -> entity.setLastModifiedOn(lastModifiedDate));
		return repository.saveAll(updatedEntities);
	}

	@Override
	public FilePathSpec getFilePathSpec(UUID documentSpaceId, UUID itemId) throws RecordNotFoundException {
		// Special case for files living at the root directory
		if (itemId.equals(NIL_UUID)) {
			return FilePathSpec.builder()
	                .documentSpaceId(documentSpaceId)
	                .itemId(NIL_UUID)
	                .itemName("")
	                .fullPathSpec("")
	                .uuidList(new ArrayList<>())
	                .parentFolderId(NIL_UUID)
	                .build();
		}
		
		Optional<DocumentSpaceFileSystemEntry> entry = repository.findByDocumentSpaceIdAndItemIdAndIsFolder(documentSpaceId, itemId, true);
		DocumentSpaceFileSystemEntry item = entry.orElse(null);
		
		if (item == null) {
			throw new RecordNotFoundException("Could not parse file path because the item: " + itemId + " does not exist");
		}
		
		Path path = Paths.get("");
		Deque<DocumentSpaceFileSystemEntry> ancestors = getAncestorHierarchy(item);
		List<UUID> idList = new ArrayList<>();

		// Append the last item to the list
		ancestors.offerLast(item);
		
		while (!ancestors.isEmpty()) {
			DocumentSpaceFileSystemEntry currentAncestor = ancestors.poll();
			path = path.resolve(currentAncestor.getItemName());
			idList.add(currentAncestor.getItemId());
		}

		return FilePathSpec.builder()
                .documentSpaceId(documentSpaceId)
                .itemId(item.getId())
                .itemName(item.getItemName())
                .fullPathSpec(path.toString())
                .uuidList(idList)
                .parentFolderId(item.getParentEntryId())
                .build();
	}

    @Override
    public String getFilePath(UUID documentSpaceId, UUID itemId) {
        return getFilePathSpec(documentSpaceId,itemId).getFullPathSpec();
    }

    /**
	 * Retrieves the ancestor hierarchy from the item. The resulting order will begin from the root
	 * and drill down to {@link from} ({@link from} is not included in the resulting hierarchy list).
	 * @param from the starting point
	 * @return an ordered ancestor list, beginning from the root
	 * @throws RecordNotFoundException throws if an ancestor no longer exists
	 */
	private Deque<DocumentSpaceFileSystemEntry> getAncestorHierarchy(DocumentSpaceFileSystemEntry from) throws RecordNotFoundException {
		DocumentSpaceFileSystemEntry currentEntry = from;
		
		Deque<DocumentSpaceFileSystemEntry> entryHierarchy = new ArrayDeque<>();
		while (!currentEntry.getParentEntryId().equals(NIL_UUID)) {
			currentEntry = repository.findByItemIdEquals(currentEntry.getParentEntryId()).orElse(null);
			
			// If, while going up the ancestor tree, an ancestor no longer exists then
			// throw an exception since the original item should also no longer exist
			if (currentEntry == null) {
				throw new RecordNotFoundException("Item no longer exists: " + from.getItemName());
			}
			
			entryHierarchy.offerFirst(currentEntry);
		}
		
		return entryHierarchy;
	}

    @Override
    public DocumentSpaceFolderInfoDto getFolderTotalSizeFromElement(FilePathSpec pathSpec) {
        DocumentSpaceFileSystemEntry entry;
        if (!pathSpec.getFullPathSpec().isBlank()) {
            entry = repository.findByItemIdEquals(pathSpec.getItemId()).orElseThrow(
                    () -> new RecordNotFoundException(String.format("Unable to lookup the item %s in the database", pathSpec.getItemName()))
            );
        } else {
            entry = DocumentSpaceFileSystemEntry.builder()
                    .documentSpaceId(pathSpec.getDocumentSpaceId())
                    .parentEntryId(NIL_UUID)
                    .itemId(NIL_UUID)
                    .isFolder(true)
                    .isDeleteArchived(false)
                    .build();
        }

        if (!entry.isFolder()) {
            throw new BadRequestException("Referenced item is not a folder");
        }

        FileSystemElementTree tree = this.dumpElementTreeFromElementEntry(entry, pathSpec, conditionPath(pathSpec.getFullPathSpec()), false);

        // get total size of the tree now...
        long size = 0;
        List<S3ObjectAndFilename> items = this.flattenTreeToS3ObjectAndFilenameList(tree);
        for (S3ObjectAndFilename item : items) {
            size += item.getS3Object().getSize();
        }

        entry.setSize(size);
        return DocumentSpaceFolderInfoDto.builder()
                .documentSpaceId(pathSpec.getDocumentSpaceId())
                .size(size)
                .count(items.size())
                .itemId(entry.getItemId())
                .itemName(entry.getItemName())
                .build();
    }
}

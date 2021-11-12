package mil.tron.commonapi.service.documentspace;

import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.model.MultiObjectDeleteException.DeleteError;

import mil.tron.commonapi.dto.documentspace.DocumentDto;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceFileSystemEntryRepository;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceRepository;
import mil.tron.commonapi.service.documentspace.util.*;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DocumentSpaceFileSystemServiceImpl implements DocumentSpaceFileSystemService {
    private final DocumentSpaceRepository documentSpaceRepository;
    private final DocumentSpaceService documentSpaceService;
    private final DocumentSpaceFileSystemEntryRepository repository;
    private final DocumentSpaceFileService documentSpaceFileService;
    private final DocumentSpaceUserCollectionService documentSpaceUserCollectionService;
    public static final String PATH_SEP = "/";
    private static final String BAD_PATH = "Path %s not found or is not a folder";

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

    /**
     * Overload that is hardwired for non-archived paths
     * @param spaceId UUID of the space
     * @param path path to find out about
     * @return the FilePathSpec describing this path
     */
    @Override
    public FilePathSpec parsePathToFilePathSpec(UUID spaceId, @Nullable String path) {
        return this.parsePathToFilePathSpec(spaceId, path, ArchivedStatus.NOT_ARCHIVED);
    }

    /**
     * Utility to find out more information about a given path within a space.  The given space
     * is of a unix-like path relative to a doc space (prefix slash is optional) - e.g. /folder1/folder2
     * @param spaceId UUID of the space
     * @param path path to find out about
     * @param archivedStatus what archived status to look for/include
     * @return the FilePathSpec describing this path
     */
    @Override
    public FilePathSpec parsePathToFilePathSpec(UUID spaceId, @Nullable String path, ArchivedStatus archivedStatus) {

        checkSpaceIsValid(spaceId);
        String lookupPath = conditionPath(path);

        UUID parentFolderId = DocumentSpaceFileSystemEntry.NIL_UUID;
        List<UUID> uuidList = new ArrayList<>();
        StringBuilder pathAccumulator = new StringBuilder();

        // dig into the path until last folder is found - this will be the parent folder
        //  all the while build out the full path leading up to this folder (names and uuids)
        //  to put for possible later use in the object's FilePathSpec object
        DocumentSpaceFileSystemEntry entry = null;
        
        Path asPath = Paths.get(lookupPath);

        for (Iterator<Path> currentPathItem = asPath.iterator(); currentPathItem.hasNext();) {
            String currentPathItemAsString = currentPathItem.next().toString();
            
            pathAccumulator.append(currentPathItemAsString).append(PATH_SEP);

            switch (archivedStatus) {
                case NOT_ARCHIVED:
                    // dont get archived items
                    entry = repository
                            .findByDocumentSpaceIdEqualsAndItemNameEqualsAndParentEntryIdEqualsAndIsDeleteArchivedEquals(spaceId, currentPathItemAsString, parentFolderId, false)
                            .orElseThrow(() -> new RecordNotFoundException(String.format(BAD_PATH, pathAccumulator.toString())));
                    break;
                case ARCHIVED:
                    // only consider archived items
                    entry = repository
                            .findByDocumentSpaceIdEqualsAndItemNameEqualsAndParentEntryIdEqualsAndIsDeleteArchivedEquals(spaceId, currentPathItemAsString, parentFolderId, true)
                            .orElseThrow(() -> new RecordNotFoundException(String.format(BAD_PATH, pathAccumulator.toString())));
                    break;
                case EITHER:
                    // all items
                    entry = repository
                            .findByDocumentSpaceIdEqualsAndItemNameEqualsAndParentEntryIdEquals(spaceId, currentPathItemAsString, parentFolderId)
                            .orElseThrow(() -> new RecordNotFoundException(String.format(BAD_PATH, pathAccumulator.toString())));
                    break;
                default:
                    throw new BadRequestException("Unknown archived status value given");
            }

            if (currentPathItem.hasNext()) parentFolderId = entry.getItemId();  // update parent ID for the next depth iteration
            uuidList.add(entry.getItemId());
        }

        return FilePathSpec.builder()
                .documentSpaceId(spaceId)
                .itemId(uuidList.isEmpty() ? DocumentSpaceFileSystemEntry.NIL_UUID : uuidList.get(uuidList.size() - 1))
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
        contents.setEntries(repository.findByDocumentSpaceIdEqualsAndParentEntryIdEqualsAndIsDeleteArchivedEquals(spaceId, spec.getItemId(), false));

        return contents;
    }

    @Override
    public List<DocumentDto> getArchivedItems(UUID spaceId) {
        List<DocumentDto> elements = new ArrayList<>();

        checkSpaceIsValid(spaceId);
        FilePathSpec entry = parsePathToFilePathSpec(spaceId, PATH_SEP, ArchivedStatus.ARCHIVED);
        DocumentSpaceFileSystemEntry element = repository.findByItemIdEquals(entry.getItemId()).orElseGet(() -> {
            if (entry.getItemId().equals(DocumentSpaceFileSystemEntry.NIL_UUID)) {
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
        while (!parentFolderId.equals(DocumentSpaceFileSystemEntry.NIL_UUID)) {

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
            if (entry.getItemId().equals(DocumentSpaceFileSystemEntry.NIL_UUID)) {
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
        List<S3ObjectSummary> files = documentSpaceService.getAllFilesInFolder(spaceId, lookupPath);
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

        List<DocumentSpaceFileSystemEntry> children = repository.findByDocumentSpaceIdEqualsAndParentEntryIdEqualsAndIsFolderTrue(spaceId, element.getItemId());
        if (children.isEmpty()) {
            return tree;  // no children under this element
        }

        if (tree.getNodes() == null) tree.setNodes(new ArrayList<>());
        for (DocumentSpaceFileSystemEntry entry : children) {
            FileSystemElementTree subTree = buildTree(spaceId, entry, new FileSystemElementTree());
            subTree.setValue(entry);
            FilePathSpec spec = convertFileSystemEntityToFilePathSpec(entry);
            List<S3ObjectSummary> files = documentSpaceService.getAllFilesInFolder(spaceId, spec.getFullPathSpec());
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

        // check no existence of duplicate (...as in no non-archived duplicate exists, a duplicate name but in archived status is allowed)
        //  so we don't get a nasty DB exception for unique constraint violation
        if (repository.existsByDocumentSpaceIdAndParentEntryIdAndItemNameAndIsDeleteArchivedEquals(spaceId, pathSpec.getItemId(), name, false)) {
            throw new ResourceAlreadyExistsException(String.format("Folder with name %s already exists under that parent", name));
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
     * Deletes a folder and all that is contained within/underneath it (like a `rm -rf` in Unix)
     * @param spaceId doc space UUID
     * @param path path to the folder to delete
     */
    @Override
    public void deleteFolder(UUID spaceId, String path) {
        FileSystemElementTree tree = dumpElementTree(spaceId, path);
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

        List<DocumentDto> archivedItems = this.getArchivedItems(spaceId);
        List<DocumentDto> itemsToUnArchive = archivedItems.stream()
                .filter(item -> items.contains(item.getKey()))
                .collect(Collectors.toList());

        for (DocumentDto item : itemsToUnArchive) {

            FilePathSpec owningElement = parsePathToFilePathSpec(spaceId, item.getPath());

            // if parent is not ROOT..
            if (!owningElement.getItemId().equals(DocumentSpaceFileSystemEntry.NIL_UUID)) {
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

    @Override
    public String joinPathParts(String... parts) {
        return (PATH_SEP + String.join(PATH_SEP, parts)).replaceAll("/+", PATH_SEP);
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
	public List<DocumentSpaceFileSystemEntry> propagateModificationStateToAncestors(
			DocumentSpaceFileSystemEntry propagateFrom) {
		Deque<DocumentSpaceFileSystemEntry> entitiesToPropagateTo = null;
		
		try {
			entitiesToPropagateTo = getAncestorHierarchy(propagateFrom);
		} catch (RecordNotFoundException ex) {
			entitiesToPropagateTo = new ArrayDeque<>();
		}
		
		if (entitiesToPropagateTo.isEmpty()) {
			return new ArrayList<>();
		}
		
		entitiesToPropagateTo.forEach(entity -> entity.setLastModifiedOn(new Date()));
		
		return repository.saveAll(entitiesToPropagateTo);
	}

	@Override
	public FilePathSpec getFilePathSpec(UUID documentSpaceId, UUID itemId) throws RecordNotFoundException {
		// Special case for files living at the root directory
		if (itemId.equals(DocumentSpaceFileSystemEntry.NIL_UUID)) {
			return FilePathSpec.builder()
	                .documentSpaceId(documentSpaceId)
	                .itemId(DocumentSpaceFileSystemEntry.NIL_UUID)
	                .itemName("")
	                .fullPathSpec("")
	                .uuidList(new ArrayList<>())
	                .parentFolderId(DocumentSpaceFileSystemEntry.NIL_UUID)
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
		while (!currentEntry.getParentEntryId().equals(DocumentSpaceFileSystemEntry.NIL_UUID)) {
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
}

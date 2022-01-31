package mil.tron.commonapi.service.documentspace;

import com.amazonaws.services.s3.model.MultiObjectDeleteException.DeleteError;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.common.collect.Lists;
import mil.tron.commonapi.entity.documentspace.DocumentSpace;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.exception.documentspace.FolderDepthException;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceFileSystemEntryRepository;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceRepository;
import mil.tron.commonapi.service.documentspace.util.FilePathSpec;
import mil.tron.commonapi.service.documentspace.util.FileSystemElementTree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.Rollback;

import javax.transaction.Transactional;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry.NIL_UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureTestDatabase
public class DocumentSpaceFileSystemServiceTests {

    @Autowired
    DocumentSpaceFileSystemService service;

    @MockBean
    DocumentSpaceService documentSpaceService;
    
    @MockBean
    DocumentSpaceFileService documentSpaceFileService;
    
    @Autowired
    DocumentSpaceFileSystemEntryRepository documentSpaceFileSystemRepository;

    @Autowired
    DocumentSpaceRepository documentSpaceRepository;
    DocumentSpace docSpace = DocumentSpace.builder()
            .id(UUID.randomUUID())
            .name("CoolSpace")
            .build();



    UUID spaceId;

    @BeforeEach
    void init() {
        documentSpaceRepository.save(docSpace);
        spaceId = docSpace.getId();
    }

    @Transactional
    @Rollback
    @Test
    void testGetFolderListInRootOfSpace() {
        assertEquals(0, service.getElementsUnderneath(spaceId, null).size());
    }

    @Transactional
    @Rollback
    @Test
    void testInvalidDocSpace() {
        assertThrows(RecordNotFoundException.class, () -> service.getElementsUnderneath(UUID.randomUUID(), null));
    }

    @Transactional
    @Rollback
    @Test
    void testAddFolders() {

        // make initial folder in root of space
        service.addFolder(spaceId, "somefolder", null);
        assertEquals(1, service.getElementsUnderneath(spaceId, null).size());

        // check existence using the "/" notation
        assertEquals(1, service.getElementsUnderneath(spaceId, "/").size());

        // check existence using the "/" notation with whitespace
        assertEquals(1, service.getElementsUnderneath(spaceId, " / ").size());

        // check existence using the "" notation for root
        assertEquals(1, service.getElementsUnderneath(spaceId, "").size());

        // duplicate fails
        assertThrows(ResourceAlreadyExistsException.class, () -> service.addFolder(spaceId, "somefolder", "/"));

        // only one folder should still be there
        assertEquals(1, service.getElementsUnderneath(spaceId, "/").size());

        // add second folder to root
        service.addFolder(spaceId, "secondfolder", "/");
        assertEquals(2, service.getElementsUnderneath(spaceId, "/").size());

        // add folder under "secondfolder"
        service.addFolder(spaceId, "subfolder", "/secondfolder/");
        assertEquals(2, service.getElementsUnderneath(spaceId, "/").size()); // still two at the root
        assertEquals(1, service.getElementsUnderneath(spaceId, "secondfolder/").size());  // one under second folder

        // add folder under "subfolder" - so we're gonna be 3 deep now
        service.addFolder(spaceId, "another_subfolder", "secondfolder/subfolder");
        assertEquals(2, service.getElementsUnderneath(spaceId, "/").size()); // still two at the root
        assertEquals(1, service.getElementsUnderneath(spaceId, "secondfolder/").size());  // one under second folder
        assertEquals(1, service.getElementsUnderneath(spaceId, "secondfolder/subfolder").size());  // one under subfolder folder

        // croak on bad path
        assertThrows(RecordNotFoundException.class, () -> service.getElementsUnderneath(spaceId, "secondfolder/invalidfolder"));
    }
    
    @Transactional
    @Rollback
    @Test
    void addFolder_shouldThrow_whenExceedsMaxFolderDepth() {
    	final StringBuilder path = new StringBuilder();
    	path.append("");
    	for (int i = 0; i < DocumentSpaceFileSystemServiceImpl.MAX_FOLDER_DEPTH; i++) {
    		service.addFolder(spaceId, Integer.toString(i), path.toString());
			path.append("/" + Integer.toString(i));
    	}
    	
    	assertThrows(FolderDepthException.class, () -> service.addFolder(spaceId, "exceeds max depth", path.toString()));
    }

    @Transactional
    @Rollback
    @Test
    void testPathSpecOps() {
        //
        //
        // make a two deep folder structure (name folders the same -- since that's a valid case)
        DocumentSpaceFileSystemEntry firstEntry = service.addFolder(spaceId, "some-folder", "/");
        DocumentSpaceFileSystemEntry secondEntry = service.addFolder(spaceId, "some-folder2", "some-folder/");

        FilePathSpec firstSpec = service.convertFileSystemEntityToFilePathSpec(firstEntry);
        assertEquals(NIL_UUID, firstEntry.getParentEntryId());
        assertEquals(firstEntry.getDocumentSpaceId(), firstSpec.getDocumentSpaceId());
        assertEquals(1, firstSpec.getUuidList().size());  // only itself cause root is owner

        FilePathSpec secondSpec = service.convertFileSystemEntityToFilePathSpec(secondEntry);
        assertEquals(firstEntry.getItemId(), secondSpec.getParentFolderId());
        assertEquals(secondEntry.getDocumentSpaceId(), secondSpec.getDocumentSpaceId());
        assertEquals(2, secondSpec.getUuidList().size()); // itself and parent (firstEntry) = 2

        //
        //
        // test out getting a path spec using string paths (vs the object entities above)
        FilePathSpec firstSpecFromPath = service.parsePathToFilePathSpec(firstEntry.getDocumentSpaceId(), "some-folder/");
        assertEquals(NIL_UUID, firstSpecFromPath.getParentFolderId());
        assertEquals(firstEntry.getDocumentSpaceId(), firstSpecFromPath.getDocumentSpaceId());
        assertEquals(1, firstSpecFromPath.getUuidList().size());  // only itself cause root is owner

        FilePathSpec secondSpecFromPath = service.parsePathToFilePathSpec(secondEntry.getDocumentSpaceId(), "some-folder/some-folder2");
        assertEquals(secondEntry.getItemId(), secondSpecFromPath.getItemId());
        assertEquals(secondEntry.getDocumentSpaceId(), secondSpecFromPath.getDocumentSpaceId());
        assertEquals(2, secondSpecFromPath.getUuidList().size()); // itself and parent (firstEntry) = 2
    }

    @Transactional
    @Rollback
    @Test
    void testGetMinioPath() {
        // test that we can get the minio path (ready to use) for addressing a folder in the doc space in Minio
        Mockito.when(documentSpaceService.getAllFilesInFolder(Mockito.any(), Mockito.anyString(), Mockito.anyBoolean()))
                .thenReturn(Lists.newArrayList());
        DocumentSpaceFileSystemEntry firstEntry = service.addFolder(spaceId, "some-folder", "/");
        DocumentSpaceFileSystemEntry secondEntry = service.addFolder(spaceId, "some-folder2", "some-folder");
        FilePathSpec spec = service.convertFileSystemEntityToFilePathSpec(secondEntry);
        assertEquals(String.format("%s/%s/%s/", spaceId, firstEntry.getItemId(), secondEntry.getItemId()), spec.getDocSpaceQualifiedPath());
    }

    @Transactional
    @Rollback
    @Test
    void testGetMinioPathOfFile() {
        // test that we can get the minio path (ready to use) for addressing a file in the doc space in Minio
        UUID spaceId = UUID.randomUUID();
        UUID grandParent = UUID.randomUUID();
        UUID parent = UUID.randomUUID();

        DocumentSpace docSpace = DocumentSpace.builder()
                .id(spaceId)
                .name("TestSpace")
                .build();
        documentSpaceRepository.save(docSpace);

        DocumentSpaceFileSystemEntry entry1 = DocumentSpaceFileSystemEntry.builder()
                .documentSpaceId(spaceId)
                .parentEntryId(NIL_UUID)
                .isFolder(true)
                .itemId(grandParent)
                .itemName("grandparent")
                .etag("etag1")
                .build();
        documentSpaceFileSystemRepository.save(entry1);

        DocumentSpaceFileSystemEntry entry2 = DocumentSpaceFileSystemEntry.builder()
                .documentSpaceId(spaceId)
                .parentEntryId(grandParent)
                .isFolder(true)
                .itemId(parent)
                .etag("etag2")
                .itemName("parent")
                .build();
        documentSpaceFileSystemRepository.save(entry2);

        DocumentSpaceFileSystemEntry entry3 = DocumentSpaceFileSystemEntry.builder()
                .documentSpaceId(spaceId)
                .parentEntryId(parent)
                .isFolder(false)
                .itemId(UUID.randomUUID())
                .etag("etag3")
                .itemName("names.txt")
                .build();
        documentSpaceFileSystemRepository.save(entry3);

        FilePathSpec spec = service.parsePathToFilePathSpec(spaceId, "grandparent/parent/names.txt");

        assertEquals(String.format("%s/%s/%s/%s", spaceId, grandParent, parent, entry3.getItemName()), spec.getDocSpaceQualifiedFilePath());
    }

    @Transactional
    @Rollback
    @Test
    void testGetElementTree() {
        // test that we can dump a given location's hierarchy

        Mockito.when(documentSpaceService.getAllFilesInFolder(Mockito.any(), Mockito.anyString(), Mockito.anyBoolean()))
                .thenReturn(Lists.newArrayList());

        service.addFolder(spaceId, "some-folder", "/");
        service.addFolder(spaceId, "some-folder2", "some-folder");
        service.addFolder(spaceId, "some-deep-folder", "/some-folder/some-folder2");

        FileSystemElementTree tree = service.dumpElementTree(spaceId, "/", false);
        assertEquals(1, tree.getNodes().size());
        assertEquals(1, tree.getNodes().get(0).getNodes().size());
        assertEquals(1, tree.getNodes().get(0).getNodes().get(0).getNodes().size());

        tree = service.dumpElementTree(spaceId, "some-folder", false);
        assertEquals(1, tree.getNodes().size());
        assertEquals(1, tree.getNodes().get(0).getNodes().size());
    }

    @Transactional
    @Rollback
    @Test
    void testDeleteFolders() {
        Mockito.when(documentSpaceService.getAllFilesInFolder(Mockito.any(), Mockito.anyString(), Mockito.anyBoolean()))
                .thenReturn(Lists.newArrayList());

        service.addFolder(spaceId, "some-folder", "/");
        service.addFolder(spaceId, "some-folder2", "some-folder");
        service.addFolder(spaceId, "some-deep-folder", "/some-folder/some-folder2");
        service.addFolder(spaceId, "some-deep-folder2", "/some-folder/some-folder2");

        assertEquals(1, service.dumpElementTree(spaceId, "/some-folder", false).getNodes().size());
        assertEquals(2, service.dumpElementTree(spaceId, "/some-folder/some-folder2", false).getNodes().size());
        assertEquals(0, service.dumpElementTree(spaceId, "/some-folder/some-folder2/some-deep-folder2", false).getNodes().size());
        service.deleteFolder(spaceId, "/some-folder/some-folder2/some-deep-folder2");
        assertEquals(1, service.dumpElementTree(spaceId, "/", false).getNodes().size());
        service.deleteFolder(spaceId, "/some-folder");
        assertEquals(0, service.dumpElementTree(spaceId, "/", false).getNodes().size());
    }
    
    @Transactional
    @Rollback
    @Test
    void testDeleteFolders_withErrors() {
        service.addFolder(spaceId, "some-folder", "/");
        service.addFolder(spaceId, "some-folder2", "some-folder");
        service.addFolder(spaceId, "some-deep-folder", "/some-folder/some-folder2");
        service.addFolder(spaceId, "some-deep-folder2", "/some-folder/some-folder2");

        DeleteError deleteErr = new DeleteError();
        deleteErr.setCode("NoSuchKey");
        
        S3ObjectSummary objSummary = new S3ObjectSummary();
        objSummary.setKey("somekey");
        
        Mockito.when(documentSpaceService.getAllFilesInFolder(Mockito.any(UUID.class), Mockito.anyString(), Mockito.anyBoolean())).thenReturn(List.of(objSummary));
        Mockito.when(documentSpaceService.deleteS3ObjectsByKey(Mockito.any())).thenReturn(List.of(deleteErr));
        
        service.deleteFolder(spaceId, "/some-folder/some-folder2/some-deep-folder2");
        
        Mockito.verify(documentSpaceFileService).deleteAllDocumentSpaceFilesInParentFolderExcept(Mockito.any(UUID.class), Mockito.any(UUID.class), Mockito.anySet());
    }

    @Transactional
    @Rollback
    @Test
    void testFolderIdTest() {
        service.addFolder(spaceId, "some-folder", "/");
        service.addFolder(spaceId, "some-folder2", "some-folder");
        service.addFolder(spaceId, "some-deep-folder", "/some-folder/some-folder2");
        service.addFolder(spaceId, "some-deep-folder2", "/some-folder/some-folder2");

        assertTrue(service.isFolder(spaceId, "/", "some-folder"));
        assertTrue(service.isFolder(spaceId, "", "some-folder"));
        assertTrue(service.isFolder(spaceId, "", "some-folder/"));
        assertTrue(service.isFolder(spaceId, "/some-folder", "some-folder2"));
        assertTrue(service.isFolder(spaceId, "/some-folder/", "some-folder2"));
        assertFalse(service.isFolder(spaceId, "/some-folder", "some-folder3"));
    }
    
    @Transactional
    @Rollback
    @Test
    void renameFolder_shouldPropagateModification_whenValidRequest() {
    	DocumentSpaceFileSystemEntry parent = service.addFolder(spaceId, "some-folder", "/");
    	service.addFolder(spaceId, "testFolder", "/some-folder");
    	
    	Date datePriorToRename = new Date();
    	service.renameFolder(spaceId, "/some-folder/testFolder", "newFolder");
    	assertThat(parent.getLastModifiedOn()).isAfter(datePriorToRename);
    }
    
    @Transactional
    @Rollback
    @Test
    void renameFolder_shouldThrow_whenNewNameAlreadyExists() {
    	service.addFolder(spaceId, "some-folder", "/");
    	service.addFolder(spaceId, "testFolder", "/");
    	
    	assertThatThrownBy(() -> service.renameFolder(spaceId, "/some-folder", "testFolder"))
    		.isInstanceOf(ResourceAlreadyExistsException.class)
    		.hasMessageContaining("A folder with that name already exists at this level");
    }

    @Transactional
    @Rollback
    @Test
    void addFolder_shouldPropagateModification_whenValidRequest() {
    	DocumentSpaceFileSystemEntry parent = service.addFolder(spaceId, "some-folder", "/");
    	assertThat(parent.getLastModifiedBy()).isNull();
    	assertThat(parent.getLastModifiedOn()).isNull();
    	
    	Date datePriorToAddFolder = new Date();
    	service.addFolder(spaceId, "testFolder", "/some-folder");
    	assertThat(parent.getLastModifiedOn()).isAfter(datePriorToAddFolder);
    }
    
    @Transactional
    @Rollback
    @Test
    void deleteFolder_shouldPropagateModification_whenValidRequest() {
    	DocumentSpaceFileSystemEntry parent = service.addFolder(spaceId, "some-folder", "/");
    	assertThat(parent.getLastModifiedBy()).isNull();
    	assertThat(parent.getLastModifiedOn()).isNull();
    	
    	service.addFolder(spaceId, "testFolder", "/some-folder");
    	
    	Date datePriorToDelete = new Date();
    	service.deleteFolder(spaceId, "/some-folder/testFolder");
    	assertThat(parent.getLastModifiedOn()).isAfter(datePriorToDelete);
    	
    }
    
    @WithMockUser(username = "test@user.com")
    @Transactional
    @Rollback
    @Test
    void propagateModificationStateToAncestors_shouldPropagateChangesUp_whenEntryContainsAncestors() {
    	DocumentSpaceFileSystemEntry parent = service.addFolder(spaceId, "some-folder", "/");
    	DocumentSpaceFileSystemEntry childLevel1 = service.addFolder(spaceId, "testFolder", "/some-folder");
    	DocumentSpaceFileSystemEntry childLevel2 = service.addFolder(spaceId, "newFolder", "/some-folder/testFolder");
    	
    	List<DocumentSpaceFileSystemEntry> propagatedEntities = service.propagateModificationStateToAncestors(childLevel2);
    	
    	assertThat(propagatedEntities).containsExactlyInAnyOrder(childLevel1, parent);
    }
    
    @WithMockUser(username = "test@user.com")
    @Transactional
    @Rollback
    @Test
    void propagateModificationStateToAncestors_shouldReturnEmptyList_whenEntryIsAtRootLevel() {
    	DocumentSpaceFileSystemEntry parent = service.addFolder(spaceId, "some-folder", "/");
    	
    	List<DocumentSpaceFileSystemEntry> propagatedEntities = service.propagateModificationStateToAncestors(parent);
    	
    	assertThat(propagatedEntities).isEmpty();
    }
    
    @WithMockUser(username = "test@user.com")
    @Transactional
    @Rollback
    @Test
    void propagateModificationStateToAncestors_shouldReturnEmptyList_whenEntryAncestorsDoNotExist() {
    	DocumentSpaceFileSystemEntry parent = service.addFolder(spaceId, "some-folder", "/");
    	DocumentSpaceFileSystemEntry childLevel1 = service.addFolder(spaceId, "testFolder", "/some-folder");
    	
    	documentSpaceFileSystemRepository.delete(parent);
    	List<DocumentSpaceFileSystemEntry> propagatedEntities = service.propagateModificationStateToAncestors(childLevel1);
    	
    	assertThat(propagatedEntities).isEmpty();
    }
    
    @Transactional
    @Rollback
    @Test
    void getFilePathSpec_shouldReturn_whenNonRootElement() {
    	DocumentSpaceFileSystemEntry firstEntry = service.addFolder(spaceId, "some-folder", "/");
        DocumentSpaceFileSystemEntry secondEntry = service.addFolder(spaceId, "some-folder2", "some-folder/");
        
        FilePathSpec spec = service.getFilePathSpec(spaceId, secondEntry.getItemId());
        
        assertThat(spec.getDocSpaceQualifiedPath()).isEqualTo(String.format("%s/%s/%s/", spaceId, firstEntry.getItemId(), secondEntry.getItemId()));
        assertThat(spec.getFullPathSpec()).isEqualTo(Paths.get("some-folder/some-folder2").toString());
    }
    
    @Transactional
    @Rollback
    @Test
    void getFilePathSpec_shouldReturnSpecialCase_whenRootElement() {
        FilePathSpec spec = service.getFilePathSpec(spaceId, NIL_UUID);
        
        assertThat(spec.getDocSpaceQualifiedPath()).isEqualTo(String.format("%s/", spaceId));
        assertThat(spec.getFullPathSpec()).isEqualTo(Paths.get("").toString());
    }

    @Transactional
    @Rollback
    @Test
    void testNearestSiblings() {

        // test that when computing most recent modified date in a folder and propogating it up to ancestors
        //  make sure we disregard files that are archived

        DocumentSpaceFileSystemEntry parentFolder = DocumentSpaceFileSystemEntry.builder()
                .itemName("Parent")
                .documentSpaceId(spaceId)
                .etag("blah")
                .isFolder(true)
                .build();

        documentSpaceFileSystemRepository.save(parentFolder);

        DocumentSpaceFileSystemEntry file1 = DocumentSpaceFileSystemEntry.builder()
                .itemName("File1")
                .documentSpaceId(spaceId)
                .parentEntryId(parentFolder.getItemId())
                .etag("blah")
                .lastModifiedOn(Date.from(LocalDateTime.of(2022, 1, 14, 12, 0).toInstant(ZoneOffset.UTC)))
                .isFolder(false)
                .build();

        DocumentSpaceFileSystemEntry file2 = DocumentSpaceFileSystemEntry.builder()
                .itemName("File2")
                .documentSpaceId(spaceId)
                .parentEntryId(parentFolder.getItemId())
                .etag("blah")
                .lastModifiedOn(Date.from(LocalDateTime.of(2022, 1, 15, 12, 0).toInstant(ZoneOffset.UTC)))
                .isFolder(false)
                .build();

        // this one should get ignored
        DocumentSpaceFileSystemEntry archivedFile = DocumentSpaceFileSystemEntry.builder()
                .itemName("Archived")
                .documentSpaceId(spaceId)
                .parentEntryId(parentFolder.getItemId())
                .lastModifiedOn(new Date())
                .isFolder(false)
                .etag("blah")
                .isDeleteArchived(true)
                .build();

        documentSpaceFileSystemRepository.saveAll(Lists.newArrayList(file1, file2, archivedFile));

        assertEquals(file2.getLastModifiedOn(),
                documentSpaceFileSystemRepository.findMostRecentModifiedDateAmongstSiblings(file1.getDocumentSpaceId(), file1.getParentEntryId())
                        .orElse(new Date()));
    }
}

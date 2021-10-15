package mil.tron.commonapi.service.documentspace;

import com.google.common.collect.Lists;
import mil.tron.commonapi.entity.documentspace.DocumentSpace;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
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
import org.springframework.test.annotation.Rollback;

import javax.transaction.Transactional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureTestDatabase
public class DocumentSpaceFileSystemServiceTests {

    @Autowired
    DocumentSpaceFileSystemService service;

    @MockBean
    DocumentSpaceService documentSpaceService;

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
    void testPathSpecOps() {
        //
        //
        // make a two deep folder structure (name folders the same -- since that's a valid case)
        DocumentSpaceFileSystemEntry firstEntry = service.addFolder(spaceId, "some-folder", "/");
        DocumentSpaceFileSystemEntry secondEntry = service.addFolder(spaceId, "some-folder2", "some-folder/");

        FilePathSpec firstSpec = service.convertFileSystemEntityToFilePathSpec(firstEntry);
        assertEquals(UUID.fromString(DocumentSpaceFileSystemEntry.NIL_UUID), firstEntry.getParentEntryId());
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
        assertEquals(UUID.fromString(DocumentSpaceFileSystemEntry.NIL_UUID), firstSpecFromPath.getParentFolderId());
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
        Mockito.when(documentSpaceService.getAllFilesInFolder(Mockito.any(), Mockito.anyString()))
                .thenReturn(Lists.newArrayList());
        DocumentSpaceFileSystemEntry firstEntry = service.addFolder(spaceId, "some-folder", "/");
        DocumentSpaceFileSystemEntry secondEntry = service.addFolder(spaceId, "some-folder2", "some-folder");
        FilePathSpec spec = service.convertFileSystemEntityToFilePathSpec(secondEntry);
        assertEquals(String.format("%s/%s/%s/", spaceId, firstEntry.getItemId(), secondEntry.getItemId()), spec.getDocSpaceQualifiedPath());
    }

    @Transactional
    @Rollback
    @Test
    void testGetElementTree() {
        // test that we can dump a given location's hierarchy

        Mockito.when(documentSpaceService.getAllFilesInFolder(Mockito.any(), Mockito.anyString()))
                .thenReturn(Lists.newArrayList());

        service.addFolder(spaceId, "some-folder", "/");
        service.addFolder(spaceId, "some-folder2", "some-folder");
        service.addFolder(spaceId, "some-deep-folder", "/some-folder/some-folder2");

        FileSystemElementTree tree = service.dumpElementTree(spaceId, "/");
        assertEquals(1, tree.getNodes().size());
        assertEquals(1, tree.getNodes().get(0).getNodes().size());
        assertEquals(1, tree.getNodes().get(0).getNodes().get(0).getNodes().size());

        tree = service.dumpElementTree(spaceId, "some-folder");
        assertEquals(1, tree.getNodes().size());
        assertEquals(1, tree.getNodes().get(0).getNodes().size());
    }

    @Transactional
    @Rollback
    @Test
    void testDeleteFolders() {
        Mockito.when(documentSpaceService.getAllFilesInFolder(Mockito.any(), Mockito.anyString()))
                .thenReturn(Lists.newArrayList());

        service.addFolder(spaceId, "some-folder", "/");
        service.addFolder(spaceId, "some-folder2", "some-folder");
        service.addFolder(spaceId, "some-deep-folder", "/some-folder/some-folder2");
        service.addFolder(spaceId, "some-deep-folder2", "/some-folder/some-folder2");

        assertEquals(1, service.dumpElementTree(spaceId, "/some-folder").getNodes().size());
        assertEquals(2, service.dumpElementTree(spaceId, "/some-folder/some-folder2").getNodes().size());
        assertEquals(0, service.dumpElementTree(spaceId, "/some-folder/some-folder2/some-deep-folder2").getNodes().size());
        service.deleteFolder(spaceId, "/some-folder/some-folder2/some-deep-folder2");
        assertEquals(1, service.dumpElementTree(spaceId, "/").getNodes().size());
        service.deleteFolder(spaceId, "/some-folder");
        assertEquals(0, service.dumpElementTree(spaceId, "/").getNodes().size());
    }

}

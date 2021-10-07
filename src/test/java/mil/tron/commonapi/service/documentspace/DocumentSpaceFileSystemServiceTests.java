package mil.tron.commonapi.service.documentspace;

import mil.tron.commonapi.entity.documentspace.DocumentSpace;
import mil.tron.commonapi.repository.documentspace.DocumentSpaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@AutoConfigureTestDatabase
public class DocumentSpaceFileSystemServiceTests {

    @Autowired
    DocumentSpaceFileSystemService service;

    @Autowired
    DocumentSpaceRepository documentSpaceRepository;

    UUID spaceId;

    @BeforeEach
    void init() {
        DocumentSpace docSpace = documentSpaceRepository.save(DocumentSpace.builder()
                .name("CoolSpace")
                .build());

        spaceId = docSpace.getId();
    }

    @Test
    void testGetFolderListInRootOfSpace() {
        assertEquals(0, service.getFolderNamesUnderneath(spaceId, null).size());
    }

    @Test
    void testAddFolderToRoot() {
        service.addFolder(spaceId, "somefolder", null);
        assertEquals(1, service.getFolderNamesUnderneath(spaceId, null).size());

        service.addFolder(spaceId, "somefolder", null);
        assertEquals(2, service.getFolderNamesUnderneath(spaceId, null).size());
    }

}

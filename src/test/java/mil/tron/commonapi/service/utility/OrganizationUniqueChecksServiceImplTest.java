package mil.tron.commonapi.service.utility;

import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public class OrganizationUniqueChecksServiceImplTest {

    @Mock
    private OrganizationRepository repository;

    @InjectMocks
    private OrganizationUniqueChecksServiceImpl uniqueService;

    @Test
    void testUniqueNameCheck() {
        Organization testOrg = Organization.builder().name("Test").build();

        Mockito.when(repository.findByNameIgnoreCase(Mockito.any(String.class))).thenReturn(Optional.of(testOrg));
        assertFalse(uniqueService.orgNameIsUnique(testOrg));

        // null name is allowed
        testOrg.setName(null);
        assertTrue(uniqueService.orgNameIsUnique(testOrg));

        // no dupes found
        testOrg.setName("test");
        Mockito.when(repository.findByNameIgnoreCase(Mockito.any(String.class))).thenReturn(Optional.empty());
        assertTrue(uniqueService.orgNameIsUnique(testOrg));
    }
}

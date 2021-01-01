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
import java.util.UUID;

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
        Organization testOrg = Organization.builder().id(UUID.randomUUID()).name("Test").build();
        Mockito.when(repository.existsById(Mockito.any(UUID.class))).thenReturn(false);
        Mockito.when(repository.findByNameIgnoreCase(Mockito.any(String.class))).thenReturn(Optional.of(testOrg));
        assertFalse(uniqueService.orgNameIsUnique(testOrg));

        // null name is allowed
        testOrg.setName(null);
        assertTrue(uniqueService.orgNameIsUnique(testOrg));

        // no dupes found
        testOrg.setName("test");
        Mockito.when(repository.findByNameIgnoreCase(Mockito.any(String.class))).thenReturn(Optional.empty());
        assertTrue(uniqueService.orgNameIsUnique(testOrg));

        // switch to existing record (update with a non-unique name)
        Mockito.when(repository.existsById(Mockito.any(UUID.class))).thenReturn(true);
        Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(new Organization()));
        Mockito.when(repository.findByNameIgnoreCase(Mockito.any(String.class))).thenReturn(Optional.of(testOrg));
        assertFalse(uniqueService.orgNameIsUnique(testOrg));

    }
}

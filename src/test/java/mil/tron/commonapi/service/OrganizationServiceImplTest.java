package mil.tron.commonapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.repository.OrganizationRepository;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceImplTest {
	@Mock
	private OrganizationRepository repository;
	
	@InjectMocks
	private OrganizationServiceImpl organizationService;
	
	private Organization testOrg;
	
	@BeforeEach
	void beforeEachSetup() {
		testOrg = new Organization();
		testOrg.setName("Some Organization");
	}
	
	@Test
	void createOrganizationTest() {
		// Test successful save
		Mockito.when(repository.save(Mockito.any(Organization.class))).thenReturn(testOrg);
		Organization createdOrg = organizationService.createOrganization(testOrg);
		assertThat(createdOrg).isEqualTo(testOrg);
		
		// Test id already exists
		Mockito.when(repository.existsById(Mockito.any(UUID.class))).thenReturn(true);
		Organization notCreatedOrg = organizationService.createOrganization(testOrg);
		assertThat(notCreatedOrg).isNull();
	}

	@Test
	void updateOrganizationTest() {
		// Test id not matching person id
    	Organization idNotMatchingOrganizationId = organizationService.updateOrganization(UUID.randomUUID(), testOrg);
    	assertThat(idNotMatchingOrganizationId).isNull();
    	
    	// Test id not exist
    	Mockito.when(repository.existsById(Mockito.any(UUID.class))).thenReturn(false);
    	Organization idNotExist = organizationService.updateOrganization(testOrg.getId(), testOrg);
    	assertThat(idNotExist).isNull();
    	
    	// Successful update
    	Mockito.when(repository.existsById(Mockito.any(UUID.class))).thenReturn(true);
    	Mockito.when(repository.save(Mockito.any(Organization.class))).thenReturn(testOrg);
    	Organization updatedOrganization = organizationService.updateOrganization(testOrg.getId(), testOrg);
    	assertThat(updatedOrganization).isEqualTo(testOrg);

	}

	@Test
	 void deleteOrganizationTest() {
		organizationService.deleteOrganization(testOrg.getId());
		Mockito.verify(repository, Mockito.times(1)).deleteById(testOrg.getId());
	}

	@Test
	void getOrganizationsTest() {
		Mockito.when(repository.findAll()).thenReturn(Arrays.asList(testOrg));
    	Iterable<Organization> persons = organizationService.getOrganizations();
    	assertThat(persons).hasSize(1);

	}

	@Test
	void getOrganizationTest() {
		// Test organization exists
    	Mockito.when(repository.findById(testOrg.getId())).thenReturn(Optional.of(testOrg));
    	Organization retrievedOrganization = organizationService.getOrganization(testOrg.getId());
    	assertThat(retrievedOrganization).isEqualTo(testOrg);
    	
    	// Test organization not exists
    	Mockito.when(repository.findById(testOrg.getId())).thenReturn(Optional.ofNullable(null));
    	Organization notExistsOrganization = organizationService.getOrganization(testOrg.getId());
    	assertThat(notExistsOrganization).isNull();

	}
}

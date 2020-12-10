package service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import mil.tron.commonapi.organization.Organization;
import mil.tron.commonapi.service.OrganizationServiceImpl;

class OrganizationServiceImplTest {
	private OrganizationServiceImpl organizationService;
	private Organization testOrg;
	
	@BeforeEach
	void beforeEachSetup() {
		organizationService = new OrganizationServiceImpl();
		
		testOrg = new Organization();
		testOrg.setName("Some Organization");
	}
	
	@Test
	void createOrganizationTest() {
		Organization createdOrg = organizationService.createOrganization(testOrg);
		assertThat(createdOrg).isEqualTo(testOrg);
		
		Organization createdOrgDuplicate = organizationService.createOrganization(testOrg);
		assertThat(createdOrgDuplicate).isNull();
		
		assertThat(organizationService.getOrganizations()).hasSize(1);
		
		organizationService.createOrganization(new Organization());
		assertThat(organizationService.getOrganizations()).hasSize(2);
	}

	@Test
	void updateOrganizationTest() {
		organizationService.createOrganization(testOrg);
		
		testOrg.setName("New Name");
		organizationService.updateOrganization(testOrg.getId(), testOrg);
		assertThat(organizationService.getOrganization(testOrg.getId()).getName()).isEqualTo(testOrg.getName());

		assertThat(organizationService.updateOrganization(UUID.randomUUID(), testOrg)).isNull();
	}

	@Test
	 void deleteOrganizationTest() {
		organizationService.createOrganization(testOrg);
		assertThat(organizationService.getOrganizations()).hasSize(1);
		
		organizationService.deleteOrganization(testOrg.getId());
		assertThat(organizationService.getOrganizations()).isEmpty();
	}

	@Test
	void getOrganizationsTest() {
		assertThat(organizationService.getOrganizations()).isEmpty();
		
		organizationService.createOrganization(new Organization());
		assertThat(organizationService.getOrganizations()).hasSize(1);
		
		organizationService.createOrganization(new Organization());
		assertThat(organizationService.getOrganizations()).hasSize(2);
		
		organizationService.createOrganization(new Organization());
		assertThat(organizationService.getOrganizations()).hasSize(3);
	}

	@Test
	void getOrganizationTest() {
		organizationService.createOrganization(testOrg);

		assertThat(organizationService.getOrganization(testOrg.getId())).isNotNull();
		assertThat(organizationService.getOrganization(testOrg.getId())).isEqualTo(testOrg);
	}
}

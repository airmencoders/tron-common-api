package mil.tron.commonapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.*;

import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.Squadron;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.PersonRepository;
import org.assertj.core.util.Lists;
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

	@Mock
	private PersonRepository personRepository;
	
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

	@Test
	void changeOrganizationLeader() {
		Person leader = new Person();
		Map<String, String> attribs = new HashMap<>();
		attribs.put("leader", leader.getId().toString());

		Mockito.when(repository.findById(testOrg.getId())).thenReturn(Optional.of(testOrg));
		Mockito.when(repository.save(Mockito.any(Organization.class))).thenReturn(testOrg);
		Mockito.when(personRepository.findById(leader.getId())).thenReturn(Optional.of(leader));
		Organization savedOrg = organizationService.modifyAttributes(testOrg.getId(), attribs);
		assertThat(savedOrg.getLeader().getId()).isEqualTo(leader.getId());

		// Test that can't accept non existent squadron ID
		Mockito.when(repository.findById(Mockito.any(UUID.class))).thenThrow(new RecordNotFoundException("Record not found"));
		assertThrows(RecordNotFoundException.class, () -> organizationService.modifyAttributes(new Organization().getId(), attribs));

		// Test that can't accept non existent leader ID
		attribs.put("leader", new Person().getId().toString());
		Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(testOrg));
		Mockito.when(personRepository.findById(Mockito.any(UUID.class))).thenThrow(new RecordNotFoundException("Record not found"));
		assertThrows(RecordNotFoundException.class, () -> organizationService.modifyAttributes(testOrg.getId(), attribs));
	}

	@Test
	void changeOrganizationName() {
		Map<String, String> attribs = new HashMap<>();
		attribs.put("name", "test org");

		Mockito.when(repository.findById(testOrg.getId())).thenReturn(Optional.of(testOrg));
		Mockito.when(repository.save(Mockito.any(Organization.class))).thenReturn(testOrg);
		Organization savedOrg = organizationService.modifyAttributes(testOrg.getId(), attribs);
		assertThat(savedOrg.getName()).isEqualTo("test org");
	}

	@Test
	void changeParentOrg() {
		Squadron newUnit = new Squadron();
		Map<String, String> attribs = new HashMap<>();
		attribs.put("parentOrganization", newUnit.getId().toString());

		Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(testOrg), Optional.of(newUnit));
		Mockito.when(repository.save(Mockito.any(Organization.class))).thenReturn(testOrg);
		Organization savedOrg = organizationService.modifyAttributes(testOrg.getId(), attribs);
		assertThat(savedOrg.getParentOrganization().getId()).isEqualTo(newUnit.getId());

		// test bogus parent
		attribs.put("parentOrganization", new Organization().getId().toString());
		Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(testOrg)).thenThrow(new RecordNotFoundException("Not Found"));
		assertThrows(RecordNotFoundException.class, () -> organizationService.modifyAttributes(testOrg.getId(), attribs));
	}

	@Test
	void addRemoveMembers() {

		Person p = new Person();
		Squadron newUnit = new Squadron();
		newUnit.setId(testOrg.getId());
		newUnit.addMember(p);

		Mockito.when(repository.findById(newUnit.getId()))
				.thenReturn(Optional.of(testOrg))
				.thenThrow(new InvalidRecordUpdateRequest("Not Found"))
				.thenThrow(new InvalidRecordUpdateRequest("Not Found"))
				.thenReturn(Optional.of(newUnit))
				.thenThrow(new RecordNotFoundException("Not Found"));

		Mockito.when(personRepository.findById(p.getId())).thenReturn(Optional.of(p));
		Mockito.when(repository.save(Mockito.any(Organization.class))).thenReturn(newUnit);

		Organization savedOrg = organizationService.addOrganizationMember(testOrg.getId(), Lists.newArrayList(p.getId()));
		assertThat(savedOrg.getMembers().size()).isEqualTo(1);

		// test fails to add bogus person
		assertThrows(InvalidRecordUpdateRequest.class, () -> organizationService.addOrganizationMember(newUnit.getId(), Lists.newArrayList(new Person().getId())));

		// test fails to remove bogus person
		assertThrows(InvalidRecordUpdateRequest.class, () -> organizationService.removeOrganizationMember(newUnit.getId(), Lists.newArrayList(new Person().getId())));

		// remove like normal
		newUnit.removeMember(p);
		savedOrg = organizationService.removeOrganizationMember(newUnit.getId(), Lists.newArrayList(p.getId()));
		assertThat(savedOrg.getMembers().size()).isEqualTo(0);

		// test bogus org Id
		assertThrows(RecordNotFoundException.class, () -> organizationService.addOrganizationMember(new Organization().getId(), Lists.newArrayList(p.getId())));
	}
}

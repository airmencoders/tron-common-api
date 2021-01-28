package mil.tron.commonapi.service;

import mil.tron.commonapi.dto.OrganizationDto;
import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.orgtypes.Unit;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.OrganizationRepository;
import mil.tron.commonapi.repository.PersonRepository;
import mil.tron.commonapi.service.utility.OrganizationUniqueChecksServiceImpl;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceImplTest {
	@Mock
	private OrganizationRepository repository;

	@Mock
	private PersonRepository personRepository;

	@Mock
	private PersonService personService;

	@Mock
	OrganizationUniqueChecksServiceImpl uniqueService;
	
	@InjectMocks
	private OrganizationServiceImpl organizationService;

	private Organization testOrg;
	private OrganizationDto testOrgDto;

	@BeforeEach
	void beforeEachSetup() {
		testOrg = new Organization();
		testOrg.setName("Some Organization");

		testOrgDto = OrganizationDto.builder()
				.id(testOrg.getId())
				.name(testOrg.getName())
				.build();
	}
	
	@Nested
	class CreateOrganizationTest {
		@Test
		void successfulSave() {
			// Test successful save
			Mockito.when(repository.save(Mockito.any(Organization.class))).thenReturn(testOrg);
			Mockito.when(uniqueService.orgNameIsUnique(Mockito.any(Organization.class))).thenReturn(true);
			OrganizationDto createdOrg = organizationService.createOrganization(testOrgDto);
			assertThat(createdOrg.getId()).isEqualTo(testOrgDto.getId());
		}
		
		@Test
		void idAlreadyExists() {
			// Test id already exists
			Mockito.when(repository.existsById(Mockito.any(UUID.class))).thenReturn(true);
			assertThatExceptionOfType(ResourceAlreadyExistsException.class).isThrownBy(() -> {
				organizationService.createOrganization(testOrgDto);
			});
		}
		
		@Test
		void testUniqueName() {
			OrganizationDto existingOrgWithSameName = new OrganizationDto();
			existingOrgWithSameName.setName(testOrgDto.getName());

			Mockito.when(uniqueService.orgNameIsUnique(Mockito.any(Organization.class))).thenReturn(false);

			assertThatExceptionOfType(ResourceAlreadyExistsException.class).isThrownBy(() -> {
				organizationService.createOrganization(testOrgDto);
			});
		}
	}
	
	@Nested
	class UpdateOrganizationTest {
		@Test
		void testIdNotMatch() {
			UUID idNotMatch = UUID.randomUUID();
			// Test id not matching person id
			assertThatExceptionOfType(InvalidRecordUpdateRequest.class).isThrownBy(() -> {
				organizationService.updateOrganization(idNotMatch, testOrgDto);
			});
		}
		
		@Test
		void testIdNotExist() {
			// Test id not exist
			Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.ofNullable(null));
	    	UUID testOrgId = testOrgDto.getId();
	    	assertThatExceptionOfType(RecordNotFoundException.class).isThrownBy(() -> {
				organizationService.updateOrganization(testOrgId, testOrgDto);
			});
		}
		
		@Test
		void testNameAlreadyExists() {
			Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(testOrg));
			
			String sameName = "Same Org";
			
			// Mock an updated org object
			Organization testOrgWithUpdatedName = new Organization();
			testOrgWithUpdatedName.setId(testOrg.getId());
			testOrgWithUpdatedName.setName(sameName);
			
			// Mock org with the same name as the updating org
			Organization existingOrgWithSameName = new Organization();
			existingOrgWithSameName.setName(sameName);

			Mockito.when(uniqueService.orgNameIsUnique(Mockito.any(Organization.class))).thenReturn(false);

			UUID testOrgId = testOrgWithUpdatedName.getId();
			
			assertThatExceptionOfType(InvalidRecordUpdateRequest.class).isThrownBy(() -> {
				organizationService.updateOrganization(testOrgId, organizationService.convertToDto(testOrgWithUpdatedName));
			});
		}
		
		@Test
		void successfulUpdate() {
			// Successful update
	    	Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(testOrg));
	    	Mockito.when(repository.save(Mockito.any(Organization.class))).thenReturn(testOrg);
			Mockito.when(uniqueService.orgNameIsUnique(Mockito.any(Organization.class))).thenReturn(true);
	    	OrganizationDto updatedOrganization = organizationService.updateOrganization(testOrg.getId(), organizationService.convertToDto(testOrg));
	    	assertThat(updatedOrganization.getName()).isEqualTo(testOrgDto.getName());
		}
	}
	
	@Nested
	class DeleteOrganizationTest {
		@Test
		 void successfulDelete() {
			Mockito.when(repository.existsById(Mockito.any(UUID.class))).thenReturn(true);
			organizationService.deleteOrganization(testOrg.getId());
			Mockito.verify(repository, Mockito.times(1)).deleteById(testOrg.getId());
		}
		
		@Test
		void deleteIdNotExist() {
			Mockito.when(repository.existsById(Mockito.any(UUID.class))).thenReturn(false);
			
			assertThatExceptionOfType(RecordNotFoundException.class).isThrownBy(() -> {
				organizationService.deleteOrganization(testOrg.getId());
			});
		}
	}

	@Test
	void getOrganizationsTest() {
		Mockito.when(repository.findAll()).thenReturn(Lists.newArrayList(testOrg));
    	Iterable<OrganizationDto> persons = organizationService.getOrganizations();
    	assertThat(persons).hasSize(1);

	}

	@Test
	void getOrganizationTest() {
		// Test organization exists
    	Mockito.when(repository.findById(testOrg.getId())).thenReturn(Optional.of(testOrg));
    	OrganizationDto retrievedOrganization = organizationService.getOrganization(testOrgDto.getId());
    	assertThat(retrievedOrganization.getId()).isEqualTo(testOrgDto.getId());
    	
    	// Test organization not exists
    	Mockito.when(repository.findById(testOrg.getId())).thenReturn(Optional.empty());
    	
    	assertThatExceptionOfType(RecordNotFoundException.class).isThrownBy(() -> {
    		organizationService.getOrganization(testOrg.getId());
    	});
	}

	@Test
	void changeOrganizationLeader() {
		Person leader = new Person();
		Map<String, String> attribs = new HashMap<>();
		attribs.put("leader", leader.getId().toString());

		Mockito.when(repository.findById(testOrg.getId())).thenReturn(Optional.of(testOrg));
		Mockito.when(repository.save(Mockito.any(Organization.class))).thenReturn(testOrg);
		Mockito.when(personRepository.findById(leader.getId())).thenReturn(Optional.of(leader));
		OrganizationDto savedOrg = organizationService.modifyAttributes(testOrg.getId(), attribs);
		assertThat(savedOrg.getLeader()).isEqualTo(leader.getId());

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
		OrganizationDto savedOrg = organizationService.modifyAttributes(testOrg.getId(), attribs);
		assertThat(savedOrg.getName()).isEqualTo("test org");
	}

	@Test
	void changeParentOrg() {
		Organization newUnit = new Organization();
		Map<String, String> attribs = new HashMap<>();
		attribs.put("parentOrganization", newUnit.getId().toString());

		Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(testOrg), Optional.of(newUnit));
		Mockito.when(repository.save(Mockito.any(Organization.class))).thenReturn(testOrg);
		OrganizationDto savedOrg = organizationService.modifyAttributes(testOrg.getId(), attribs);
		assertThat(savedOrg.getParentOrganization()).isEqualTo(newUnit.getId());

		// test bogus parent
		attribs.put("parentOrganization", new Organization().getId().toString());
		Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(testOrg)).thenThrow(new RecordNotFoundException("Not Found"));
		assertThrows(RecordNotFoundException.class, () -> organizationService.modifyAttributes(testOrgDto.getId(), attribs));
	}

	@Test
	void changeIdFails() {
		Organization newUnit = new Organization();
		Map<String, String> attribs = new HashMap<>();
		attribs.put("id", newUnit.getId().toString());

		// can't patch/change an org's ID
		Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(testOrg));
		assertThrows(InvalidRecordUpdateRequest.class, () -> organizationService.modifyAttributes(testOrgDto.getId(), attribs));
	}

	@Test
	void addRemoveMembers() {

		Person p = new Person();
		Organization newUnit = new Organization();
		newUnit.setId(testOrgDto.getId());
		newUnit.addMember(p);

		Mockito.when(repository.findById(newUnit.getId()))
				.thenReturn(Optional.of(testOrg))
				.thenThrow(new InvalidRecordUpdateRequest("Not Found"))
				.thenThrow(new InvalidRecordUpdateRequest("Not Found"))
				.thenReturn(Optional.of(newUnit))
				.thenThrow(new RecordNotFoundException("Not Found"));

		Mockito.when(personRepository.findById(p.getId())).thenReturn(Optional.of(p));
		Mockito.when(repository.save(Mockito.any(Organization.class))).thenReturn(newUnit);

		OrganizationDto savedOrg = organizationService.addOrganizationMember(testOrgDto.getId(), Lists.newArrayList(p.getId()));
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

	@Test
	void addRemoveSubordinateOrgs() {
		Organization subOrg = new Organization();

		Organization newUnit = new Organization();
		newUnit.setId(testOrgDto.getId());
		newUnit.addSubordinateOrganization(subOrg);

		Mockito.when(repository.findById(Mockito.any(UUID.class)))
				.thenReturn(Optional.of(newUnit))
				.thenReturn(Optional.of(subOrg))
				.thenReturn(Optional.of(newUnit))
				.thenThrow(new InvalidRecordUpdateRequest("Not Found"))
				.thenReturn(Optional.of(newUnit))
				.thenThrow(new InvalidRecordUpdateRequest("Not Found"))
				.thenReturn(Optional.of(newUnit))
				.thenReturn(Optional.of(subOrg))
				.thenThrow(new RecordNotFoundException("Not Found"));

		Mockito.when(repository.save(Mockito.any(Organization.class))).thenReturn(newUnit);

		OrganizationDto savedOrg = organizationService.addSubordinateOrg(testOrgDto.getId(), Lists.newArrayList(subOrg.getId()));
		assertThat(savedOrg.getSubordinateOrganizations().size()).isEqualTo(1);

		// test fails to add bogus subordinate organization
		assertThrows(InvalidRecordUpdateRequest.class, () -> organizationService.addSubordinateOrg(newUnit.getId(), Lists.newArrayList(new Organization().getId())));

		// test fails to remove bogus subordinate organization
		assertThrows(InvalidRecordUpdateRequest.class, () -> organizationService.removeSubordinateOrg(newUnit.getId(), Lists.newArrayList(new Organization().getId())));

		// remove like normal
		newUnit.removeSubordinateOrganization(subOrg);
		savedOrg = organizationService.removeSubordinateOrg(newUnit.getId(), Lists.newArrayList(subOrg.getId()));
		assertThat(savedOrg.getMembers().size()).isEqualTo(0);

		// test bogus org Id
		assertThrows(RecordNotFoundException.class, () -> organizationService.addSubordinateOrg(new Organization().getId(), Lists.newArrayList(subOrg.getId())));

	}

	@Test
	void testBulkAddOrgs() {
		Mockito.when(repository.save(Mockito.any(Organization.class))).then(returnsFirstArg());
		List<OrganizationDto> newOrgs = Lists.newArrayList(
				organizationService.convertToDto(new Organization()),
				organizationService.convertToDto(new Organization())
		);

		Mockito.when(uniqueService.orgNameIsUnique(Mockito.any(Organization.class))).thenReturn(true);
		List<OrganizationDto> addedOrgs = organizationService.bulkAddOrgs(newOrgs);
		assertEquals(newOrgs, addedOrgs);
	}

	@Test
	void testMapToDto() {
		Person leader = new Person();
		Organization parent = new Organization();
		Organization subord = new Organization();
		Organization org = Organization.builder()
				.id(UUID.randomUUID())
				.leader(leader)
				.parentOrganization(parent)
				.subordinateOrganizations(Set.of(subord))
				.name("Test1")
				.members(Set.of(leader))
				.orgType(Unit.ORGANIZATION)
				.build();

		OrganizationDto dto = new ModelMapper().map(org, OrganizationDto.class);
		assertEquals(dto, organizationService.convertToDto(org));
	}

	@Test
	void testDtoToOrg() {
		Person leader = new Person();
		Organization parent = new Organization();
		Organization subord = new Organization();
		Organization org = Organization.builder()
				.id(UUID.randomUUID())
				.leader(leader)
				.parentOrganization(parent)
				.subordinateOrganizations(Set.of(subord))
				.name("Test1")
				.members(Set.of(leader))
				.build();
		OrganizationDto dto = new ModelMapper().map(org, OrganizationDto.class);
		Mockito.when(repository.findById(parent.getId())).thenReturn(Optional.of(parent));
		Mockito.when(personService.getPerson(leader.getId())).thenReturn(leader);

		assertEquals(org, organizationService.convertToEntity(dto));

	}
}

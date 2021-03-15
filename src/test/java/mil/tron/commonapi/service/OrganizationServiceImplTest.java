package mil.tron.commonapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import mil.tron.commonapi.dto.OrganizationDto;
import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.orgtypes.Unit;
import mil.tron.commonapi.pubsub.messages.PubSubMessage;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.pubsub.EventManagerServiceImpl;
import mil.tron.commonapi.repository.OrganizationMetadataRepository;
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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.*;
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
	private OrganizationMetadataRepository organizationMetadataRepository;

	@Mock
	private EventManagerServiceImpl eventManagerService;

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
		testOrg.setOrgType(Unit.SQUADRON);
		testOrg.setBranchType(Branch.USAF);

		testOrgDto = OrganizationDto.builder()
				.id(testOrg.getId())
				.name(testOrg.getName())
				.orgType(Unit.SQUADRON)
				.branchType(Branch.USAF)
				.build();
	}
	
	@Nested
	class CreateOrganizationTest {
		@Test
		void successfulSave() {
			// Test successful save
			Mockito.when(repository.save(Mockito.any(Organization.class))).thenReturn(testOrg);
			Mockito.when(uniqueService.orgNameIsUnique(Mockito.any(Organization.class))).thenReturn(true);
			Mockito.doNothing().when(eventManagerService).recordEventAndPublish(Mockito.any(PubSubMessage.class));
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
			Mockito.doNothing().when(eventManagerService).recordEventAndPublish(Mockito.any(PubSubMessage.class));
	    	OrganizationDto updatedOrganization = organizationService.updateOrganization(testOrg.getId(), organizationService.convertToDto(testOrg));
	    	assertThat(updatedOrganization.getName()).isEqualTo(testOrgDto.getName());
		}
	}
	
	@Nested
	class DeleteOrganizationTest {
		@Test
		 void successfulDelete() {
			Mockito.when(repository.existsById(Mockito.any(UUID.class))).thenReturn(true);
			Mockito.doNothing().when(eventManagerService).recordEventAndPublish(Mockito.any(PubSubMessage.class));
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
    	Iterable<OrganizationDto> persons = organizationService.getOrganizations("");
    	assertThat(persons).hasSize(1);

	}

	@Test
	void getOrganizationsByTypeAndServiceTest() {
		Mockito.when(repository.findAll()).thenReturn(Lists.newArrayList(testOrg));
		Iterable<OrganizationDto> persons = organizationService.getOrganizationsByTypeAndService("", Unit.SQUADRON, Branch.USAF);
		assertThat(persons).hasSize(1);

		persons = organizationService.getOrganizationsByTypeAndService("", Unit.SQUADRON, null);
		assertThat(persons).hasSize(1);

		persons = organizationService.getOrganizationsByTypeAndService("", Unit.WING, null);
		assertThat(persons).hasSize(0);

		persons = organizationService.getOrganizationsByTypeAndService("", null, null);
		assertThat(persons).hasSize(1);

		persons = organizationService.getOrganizationsByTypeAndService("", null, Branch.USAF);
		assertThat(persons).hasSize(1);

		persons = organizationService.getOrganizationsByTypeAndService("some org", null, Branch.USAF);
		assertThat(persons).hasSize(1);

		persons = organizationService.getOrganizationsByTypeAndService("some org", null, null);
		assertThat(persons).hasSize(1);

		persons = organizationService.getOrganizationsByTypeAndService("area 51", null, null);
		assertThat(persons).hasSize(0);

		persons = organizationService.getOrganizationsByTypeAndService("", Unit.SQUADRON, Branch.USMC);
		assertThat(persons).hasSize(0);

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
		OrganizationDto savedOrg = organizationService.modify(testOrg.getId(), attribs);
		assertThat(savedOrg.getLeader()).isEqualTo(leader.getId());

		// Test that can't accept non existent squadron ID
		Mockito.when(repository.findById(Mockito.any(UUID.class))).thenThrow(new RecordNotFoundException("Record not found"));
		assertThrows(RecordNotFoundException.class, () -> organizationService.modify(new Organization().getId(), attribs));

		// Test that can't accept non existent leader ID
		attribs.put("leader", new Person().getId().toString());
		Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(testOrg));
		Mockito.when(personRepository.findById(Mockito.any(UUID.class))).thenThrow(new RecordNotFoundException("Record not found"));
		assertThrows(RecordNotFoundException.class, () -> organizationService.modify(testOrg.getId(), attribs));
	}

	@Test
	void changeOrganizationBranchAndUnitType() {
		Map<String, String> attribs = new HashMap<>();
		attribs.put("branchType", Branch.USAF.toString());
		attribs.put("orgType", Unit.OTHER_USAF.toString());

		Mockito.when(repository.findById(testOrg.getId())).thenReturn(Optional.of(testOrg));
		Mockito.when(repository.save(Mockito.any(Organization.class))).thenReturn(testOrg);
		OrganizationDto savedOrg = organizationService.modify(testOrg.getId(), attribs);
		assertThat(savedOrg.getBranchType()).isEqualTo(Branch.USAF);
		assertThat(savedOrg.getOrgType()).isEqualTo(Unit.OTHER_USAF);
	}

	@Test
	void changeOrganizationName() {
		Map<String, String> attribs = new HashMap<>();
		attribs.put("name", "test org");

		Mockito.when(repository.findById(testOrg.getId())).thenReturn(Optional.of(testOrg));
		Mockito.when(repository.save(Mockito.any(Organization.class))).thenReturn(testOrg);
		OrganizationDto savedOrg = organizationService.modify(testOrg.getId(), attribs);
		assertThat(savedOrg.getName()).isEqualTo("test org");
	}

	@Test
	void changeParentOrg() {
		Organization newUnit = new Organization();
		Map<String, String> attribs = new HashMap<>();
		attribs.put("parentOrganization", newUnit.getId().toString());

		Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(testOrg), Optional.of(newUnit));
		Mockito.when(repository.save(Mockito.any(Organization.class))).thenReturn(testOrg);
		OrganizationDto savedOrg = organizationService.modify(testOrg.getId(), attribs);
		assertThat(savedOrg.getParentOrganization()).isEqualTo(newUnit.getId());

		// test bogus parent
		attribs.put("parentOrganization", new Organization().getId().toString());
		Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(testOrg)).thenThrow(new RecordNotFoundException("Not Found"));
		assertThrows(RecordNotFoundException.class, () -> organizationService.modify(testOrgDto.getId(), attribs));
	}

	@Test
	void changeIdFails() {
		Organization newUnit = new Organization();
		Map<String, String> attribs = new HashMap<>();
		attribs.put("id", newUnit.getId().toString());

		// can't patch/change an org's ID
		Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(testOrg));
		assertThrows(InvalidRecordUpdateRequest.class, () -> organizationService.modify(testOrgDto.getId(), attribs));
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
		Mockito.doNothing().when(eventManagerService).recordEventAndPublish(Mockito.any(PubSubMessage.class));
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
		Mockito.doNothing().when(eventManagerService).recordEventAndPublish(Mockito.any(PubSubMessage.class));
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
		Mockito.doNothing().when(eventManagerService).recordEventAndPublish(Mockito.any(PubSubMessage.class));
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
				.branchType(Branch.USAF)
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
		Mockito.when(repository.findById(subord.getId())).thenReturn(Optional.of(subord));
		Mockito.when(personService.getPerson(leader.getId())).thenReturn(leader);

		assertEquals(org, organizationService.convertToEntity(dto));

	}

	@Test
	void testCustomizeEntity() {
		Person leader = Person.builder().id(UUID.randomUUID()).email("cz@test.com").build();
		Person member = Person.builder().id(UUID.randomUUID()).email("bm@test.com").build();
		Organization parent = new Organization();
		Organization subord = new Organization();
		Organization org = Organization.builder()
				.id(UUID.randomUUID())
				.leader(leader)
				.parentOrganization(parent)
				.subordinateOrganizations(Set.of(subord))
				.name("Test1")
				.members(Set.of(leader, member))
				.orgType(Unit.WING)
				.branchType(Branch.USAF)
				.build();

		OrganizationDto dto = OrganizationDto.builder()
				.id(org.getId())
				.leader(org.getLeader().getId())
				.parentOrganization(org.getParentOrganization().getId())
				.branchType(org.getBranchType())
				.orgType(org.getOrgType())
				.members(org.getMembers().stream().map(Person::getId).collect(Collectors.toSet()))
				.subordinateOrganizations(org.getSubordinateOrganizations().stream().map(Organization::getId).collect(Collectors.toSet()))
				.name(org.getName())
				.build();

		Mockito.when(personService.getPerson(leader.getId())).thenReturn(leader);
		Mockito.when(personService.getPerson(member.getId())).thenReturn(member);
		Mockito.when(repository.findById(parent.getId())).thenReturn(Optional.of(parent));
		Mockito.when(repository.findById(subord.getId())).thenReturn(Optional.of(subord));

		Map<String, String> fields = new HashMap<>();
		fields.put("organizations", "id,name");
		fields.put("people", "id,firstName");

		JsonNode node = organizationService.customizeEntity(fields, dto);

		// check that nested member persons have two fields
		assertEquals(2, Lists.newArrayList(node.get("members").get(0).elements()).size());
		assertTrue(node.get("members").get(0).has("id"));
		assertTrue(node.get("members").get(0).has("firstName"));
		assertFalse(node.get("members").get(0).has("lastName"));

		// check that leader (another person type field) has its two elements, none more
		assertEquals(2, Lists.newArrayList(node.get("leader").elements()).size());
		assertTrue(node.get("leader").has("id"));
		assertTrue(node.get("leader").has("firstName"));
		assertFalse(node.get("leader").has("lastName"));

		assertEquals(2, Lists.newArrayList(node.get("parentOrganization").elements()).size());
		assertTrue(node.get("parentOrganization").has("id"));
		assertTrue(node.get("parentOrganization").has("name"));
		assertFalse(node.get("parentOrganization").has("leader"));

		assertEquals(2, Lists.newArrayList(node.get("subordinateOrganizations").get(0).elements()).size());
		assertTrue(node.get("subordinateOrganizations").get(0).has("id"));
		assertTrue(node.get("subordinateOrganizations").get(0).has("name"));
		assertFalse(node.get("subordinateOrganizations").get(0).has("leader"));

		// check that we can't put that we want 'subordinateOrganizations' and 'parentOrganizations'
		//  in the nested entities of the output
		fields.put("organizations", "id,name,parentOrganization,subordinateOrganization");

		// check nested members and orgs don't themselves have nested members and orgs
		node = organizationService.customizeEntity(fields, dto);
		assertEquals(2, Lists.newArrayList(node.get("parentOrganization").elements()).size());
		assertTrue(node.get("parentOrganization").has("id"));
		assertTrue(node.get("parentOrganization").has("name"));
		assertFalse(node.get("parentOrganization").has("parentOrganization"));
		assertFalse(node.get("parentOrganization").has("members"));
		assertFalse(node.get("parentOrganization").has("subordinateOrganizations"));

		// remove the people/org criteria, code should auto place 'id' at least
		fields.put("organizations", "");
		fields.put("people", "");

		node = organizationService.customizeEntity(fields, dto);
		assertEquals(1, Lists.newArrayList(node.get("members").get(0).elements()).size());
		assertTrue(node.get("members").get(0).has("id"));
		assertFalse(node.get("members").get(0).has("firstName"));

		assertEquals(1, Lists.newArrayList(node.get("parentOrganization").elements()).size());
		assertTrue(node.get("parentOrganization").has("id"));
		assertFalse(node.get("parentOrganization").has("name"));

		// test junk criteria is ignored
		fields.put("organizations", "id, name, junkk_34  sfd");
		fields.put("people", "id, firstName, test222");

		node = organizationService.customizeEntity(fields, dto);
		assertEquals(2, Lists.newArrayList(node.get("members").get(0).elements()).size());
		assertTrue(node.get("members").get(0).has("id"));
		assertTrue(node.get("members").get(0).has("firstName"));
		assertFalse(node.get("members").get(0).has("lastName"));

		// check that leader (another person type field) has its two elements, none more
		assertEquals(2, Lists.newArrayList(node.get("leader").elements()).size());
		assertTrue(node.get("leader").has("id"));
		assertTrue(node.get("leader").has("firstName"));
		assertFalse(node.get("leader").has("lastName"));

		assertEquals(2, Lists.newArrayList(node.get("parentOrganization").elements()).size());
		assertTrue(node.get("parentOrganization").has("id"));
		assertTrue(node.get("parentOrganization").has("name"));
		assertFalse(node.get("parentOrganization").has("leader"));
	}

	void testThatOrgCantAssignSubordinateOrgThatsInItsAncestryChain() {

		Organization greatGrandParent = new Organization();
		Organization grandParent = new Organization();
		Organization parent = new Organization();
		Organization theOrg = new Organization();
		Organization legitSubOrg = new Organization();

		// build the family tree
		theOrg.setParentOrganization(parent);
		parent.addSubordinateOrganization(theOrg);
		parent.setParentOrganization(grandParent);
		grandParent.setParentOrganization(greatGrandParent);
		grandParent.addSubordinateOrganization(parent);
		greatGrandParent.addSubordinateOrganization(grandParent);


		// should return true since the greatGrandParent cannot be added as a subordinate of 'theOrg'
		assertTrue(organizationService.orgIsInAncestryChain(greatGrandParent.getId(), theOrg));

		// should return true since the greatGrandParent cannot be added as a subordinate of 'theOrg'
		assertFalse(organizationService.orgIsInAncestryChain(legitSubOrg.getId(), theOrg));

	}
}

package mil.tron.commonapi.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.dto.OrganizationDto;
import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.orgtypes.Unit;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.service.OrganizationService;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrganizationController.class)
public class OrganizationControllerTest {
	private static final String ENDPOINT = "/v1/organization/";
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Autowired
	private MockMvc mockMvc;
	
	@MockBean
	private OrganizationService organizationService;

	private Person testPerson;
	private Person testLeaderPerson;
	private Organization testOrg;
	private OrganizationDto testOrgDto;

	@BeforeEach
	public void beforeEachTest() throws JsonProcessingException {
		testPerson = new Person();
		testPerson.setFirstName("Test");
		testPerson.setLastName("Person");
		testPerson.setMiddleName("MVC");
		testPerson.setTitle("Person Title");
		testPerson.setEmail("test.person@mvc.com");
		
		testLeaderPerson = new Person();
		testLeaderPerson.setFirstName("Test");
		testLeaderPerson.setLastName("Person");
		testLeaderPerson.setMiddleName("Leader");
		testLeaderPerson.setTitle("Leader Person");
		testLeaderPerson.setEmail("test.leader@person.com");

		testOrg = new Organization();
		testOrg.setName("Test Org");
		testOrg.setLeader(testLeaderPerson);
		testOrg.addMember(testPerson);

		testOrgDto = OrganizationDto.builder()
				.id(testOrg.getId())
				.leader(testOrg.getLeader().getId())
				.name(testOrg.getName())
				.orgType(Unit.WING)
				.branchType(Branch.USAF)
				.members(testOrg.getMembers().stream().map(Person::getId).collect(Collectors.toSet()))
				.build();

	}
	
	@Nested
	class TestGet {

		@Test
		void testGetAll() throws Exception {
			List<OrganizationDto> orgs = new ArrayList<>();
			orgs.add(testOrgDto);

			Mockito.when(organizationService.getOrganizations("")).thenReturn(orgs);
			
			mockMvc.perform(get(ENDPOINT))
				.andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(orgs)));
		}

		@Test
		void testGetAllByType() throws Exception {
			List<OrganizationDto> orgs = new ArrayList<>();
			orgs.add(testOrgDto);

			Mockito.when(organizationService.getOrganizationsByTypeAndService("", Unit.WING, null)).thenReturn(orgs);

			mockMvc.perform(get(ENDPOINT + "?type=WING"))
					.andExpect(status().isOk())
					.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(orgs)));
		}

		@Test
		void testGetAllByTypeAndService() throws Exception {
			List<OrganizationDto> orgs = new ArrayList<>();
			orgs.add(testOrgDto);

			Mockito.when(organizationService.getOrganizationsByTypeAndService("", Unit.WING, Branch.USAF)).thenReturn(orgs);

			mockMvc.perform(get(ENDPOINT + "?type=WING&branch=USAF"))
					.andExpect(status().isOk())
					.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(orgs)));

			Mockito.when(organizationService.getOrganizationsByTypeAndService("", null, null)).thenReturn(new ArrayList<>());
			mockMvc.perform(get(ENDPOINT + "?type=UNKNOWN&branch=UNKNOWN"))
					.andExpect(status().isOk())
					.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(new ArrayList<>())));

			Mockito.when(organizationService.getOrganizationsByTypeAndService("", null, Branch.USAF)).thenReturn(orgs);
			mockMvc.perform(get(ENDPOINT + "?type=UNKNOWN&branch=USAF"))
					.andExpect(status().isOk())
					.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(orgs)));

			mockMvc.perform(get(ENDPOINT + "?type=UNKNOWN&branch=BLAH"))
					.andExpect(status().isBadRequest());

		}
		
		@Test
		void testGetById() throws Exception {
			Mockito.when(organizationService.getOrganization(Mockito.any(UUID.class))).thenReturn(testOrgDto);
			
			mockMvc.perform(get(ENDPOINT + "{id}", testOrgDto.getId()))
				.andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(testOrgDto)));
		}
		
		@Test
		void testGetByIdNotFound() throws Exception {
			Mockito.when(organizationService.getOrganization(Mockito.any(UUID.class))).thenThrow(RecordNotFoundException.class);
			
			mockMvc.perform(get(ENDPOINT + "{id}", testOrgDto.getId()))
				.andExpect(status().isNotFound());
		}
		
		@Test
		void testGetByIdBadPathVariable() throws Exception {
			// Send an invalid UUID as ID path variable
			mockMvc.perform(get(ENDPOINT + "{id}", "asdf1234"))
				.andExpect(status().isBadRequest())
				.andExpect(result -> assertTrue(result.getResolvedException() instanceof MethodArgumentTypeMismatchException));
		}

		@Test
		void testGetOrganizationsTerse() throws Exception {
			ModelMapper mapper = new ModelMapper();
			OrganizationDto dto = mapper.map(testOrgDto, OrganizationDto.class);
			String dtoStr = OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(dto));
			Mockito.when(organizationService.getOrganizations("")).thenReturn(Lists.newArrayList(testOrgDto));
			Mockito.when(organizationService.convertToDto(testOrg)).thenReturn(dto);
			mockMvc.perform(get(ENDPOINT + "?onlyIds=true"))
					.andExpect(status().isOk())
					.andExpect(result -> assertEquals(dtoStr, result.getResponse().getContentAsString()));
		}

		@Test
		void testGetOrganizationByIdTerse() throws Exception {
			ModelMapper mapper = new ModelMapper();
			OrganizationDto dto = mapper.map(testOrgDto, OrganizationDto.class);
			String dtoStr = OBJECT_MAPPER.writeValueAsString(dto);
			Mockito.when(organizationService.getOrganization(testOrgDto.getId())).thenReturn(testOrgDto);
			Mockito.when(organizationService.convertToDto(testOrg)).thenReturn(dto);
			mockMvc.perform(get(ENDPOINT + "/{id}?onlyIds=true", testOrgDto.getId()))
					.andExpect(status().isOk())
					.andExpect(result -> assertEquals(dtoStr, result.getResponse().getContentAsString()));
		}
	}
	
	@Nested
	class TestPost {
		@Test
		void testPostValidJsonBody() throws Exception {
			Mockito.when(organizationService.createOrganization(Mockito.any(OrganizationDto.class))).thenReturn(testOrgDto);
			
			mockMvc.perform(post(ENDPOINT)
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(OBJECT_MAPPER.writeValueAsString(testOrgDto)))
				.andExpect(status().isCreated())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(testOrgDto)));
		}
		
		@Test
		void testPostInvalidJsonBody() throws Exception {
			// Send empty string as bad json data
			mockMvc.perform(post(ENDPOINT).accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).content(""))
				.andExpect(status().isBadRequest())
				.andExpect(result -> assertTrue(result.getResolvedException() instanceof HttpMessageNotReadableException));
		}
		
		@Test
		void testPostOrganizationWithIdAlreadyExists() throws Exception {
			Mockito.when(organizationService.createOrganization(Mockito.any(OrganizationDto.class))).thenThrow(ResourceAlreadyExistsException.class);
			
			mockMvc.perform(post(ENDPOINT)
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(OBJECT_MAPPER.writeValueAsString(testOrgDto)))
				.andExpect(status().isConflict());
		}

		@Test
		void testBulkCreate() throws Exception {
			List<OrganizationDto> newOrgs = Lists.newArrayList(
					organizationService.convertToDto(new Organization()),
					organizationService.convertToDto(new Organization()),
					organizationService.convertToDto(new Organization()),
					organizationService.convertToDto(new Organization())
			);

			Mockito.when(organizationService.bulkAddOrgs(Mockito.anyList())).then(returnsFirstArg());

			mockMvc.perform(post(ENDPOINT + "/organizations")
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(OBJECT_MAPPER.writeValueAsString(newOrgs)))
					.andExpect(status().isCreated())
					.andExpect(result -> assertEquals(OBJECT_MAPPER.writeValueAsString(newOrgs), result.getResponse().getContentAsString()));

		}
	}
	
	@Nested
	class TestPut {
		@Test
		void testPutValidJsonBody() throws Exception {
			Mockito.when(organizationService.updateOrganization(Mockito.any(UUID.class), Mockito.any(OrganizationDto.class))).thenReturn(testOrgDto);
			
			mockMvc.perform(put(ENDPOINT + "{id}", testOrgDto.getId())
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(OBJECT_MAPPER.writeValueAsString(testOrgDto)))
				.andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(testOrgDto)));
		}
		
		@Test
		void testPutInvalidJsonBody() throws Exception {
			// Send empty string as bad json data
			mockMvc.perform(put(ENDPOINT + "{id}", testOrgDto.getId())
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(""))
				.andExpect(status().isBadRequest())
				.andExpect(result -> assertTrue(result.getResolvedException() instanceof HttpMessageNotReadableException));
		}
		
		@Test
		void testPutInvalidBadPathVariable() throws Exception {
			// Send an invalid UUID as ID path variable
			mockMvc.perform(put(ENDPOINT + "{id}", "asdf1234"))
				.andExpect(status().isBadRequest())
				.andExpect(result -> assertTrue(result.getResolvedException() instanceof MethodArgumentTypeMismatchException));
		}
		
		@Test
		void testPutResourceDoesNotExist() throws Exception {
			Mockito.when(organizationService.updateOrganization(Mockito.any(UUID.class), Mockito.any(OrganizationDto.class))).thenReturn(null);

			mockMvc.perform(put(ENDPOINT + "{id}", testOrg.getId())
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(OBJECT_MAPPER.writeValueAsString(testOrgDto)))
				.andExpect(status().isNotFound());
		}
	}
	
	@Nested
	class TestDelete {
		@Test
		void testDelete() throws Exception {
			mockMvc.perform(delete(ENDPOINT + "{id}", testOrgDto.getId()))
				.andExpect(status().isNoContent());
		}
		
		@Test
		void testDeleteBadPathVariable() throws Exception {
			mockMvc.perform(delete(ENDPOINT + "{id}", "asdf1234"))
				.andExpect(status().isBadRequest())
				.andExpect(result -> assertTrue(result.getResolvedException() instanceof MethodArgumentTypeMismatchException));
		}
	}

	@Nested
	class TestPatch {
		@Test
		void testChangeName() throws Exception {
			Map<String, String> attribs = new HashMap<>();
			attribs.put("name", "test org");
			OrganizationDto newOrg = new OrganizationDto();
			newOrg.setId(testOrgDto.getId());
			newOrg.setName("test org");

			Mockito.when(organizationService.modifyAttributes(Mockito.any(UUID.class), Mockito.any(Map.class))).thenReturn(newOrg);
			MvcResult result = mockMvc.perform(patch(ENDPOINT + "{id}", testOrgDto.getId())
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(OBJECT_MAPPER.writeValueAsString(attribs)))
					.andExpect(status().isOk())
					.andReturn();

			assertEquals("test org", OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), OrganizationDto.class).getName());
		}

		@Test
		void testAddRemoveMember() throws Exception {
			Person p = new Person();

			OrganizationDto newOrg = new OrganizationDto();
			newOrg.setId(testOrgDto.getId());
			newOrg.setName("test org");
			newOrg.getMembers().add(p.getId());

			Mockito.when(organizationService.addOrganizationMember(Mockito.any(UUID.class), Mockito.any(List.class))).thenReturn(newOrg);
			MvcResult result = mockMvc.perform(patch(ENDPOINT + "{id}/members", testOrgDto.getId())
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(p.getId()))))
					.andExpect(status().isOk())
					.andReturn();

			// test it "added" to org
			assertEquals(1, OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), OrganizationDto.class).getMembers().size());

			newOrg.getMembers().remove(p.getId());
			Mockito.when(organizationService.removeOrganizationMember(Mockito.any(UUID.class), Mockito.any(List.class))).thenReturn(newOrg);
			MvcResult result2 = mockMvc.perform(delete(ENDPOINT + "{id}/members", testOrgDto.getId())
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(p.getId()))))
					.andExpect(status().isOk())
					.andReturn();

			// test it "removed" from org
			assertEquals(0, OBJECT_MAPPER.readValue(result2.getResponse().getContentAsString(), OrganizationDto.class).getMembers().size());
		}

		@Test
		void testAddRemoveSubordinateOrgs() throws Exception {
			Organization subOrg = new Organization();

			OrganizationDto newOrg = new OrganizationDto();
			newOrg.setId(testOrgDto.getId());
			newOrg.setName("test org");
			newOrg.getSubordinateOrganizations().add(subOrg.getId());

			Mockito.when(organizationService.addSubordinateOrg(Mockito.any(UUID.class), Mockito.anyList())).thenReturn(newOrg);
			mockMvc.perform(patch(ENDPOINT + "{id}/subordinates", testOrgDto.getId())
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(subOrg.getId()))))
					.andExpect(status().isOk());

			newOrg.getSubordinateOrganizations().remove(subOrg.getId());
			mockMvc.perform(delete(ENDPOINT + "{id}/subordinates", testOrgDto.getId())
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(OBJECT_MAPPER.writeValueAsString(Lists.newArrayList(subOrg.getId()))))
					.andExpect(status().isOk());
		}

		@Test
		void testFlattenOrg() throws Exception {
			OrganizationDto newOrg = new OrganizationDto();
			newOrg.setId(UUID.randomUUID());
			newOrg.setName("test org Child");

			testOrgDto.setSubOrgsUUID(Set.of(newOrg.getId()));

			Person p = new Person();
			Person p2 = new Person();
			newOrg.setMembersUUID(Set.of(p.getId(), p2.getId()));

			Mockito.when(organizationService.getOrganization(newOrg.getId())).thenReturn(newOrg);
			Mockito.when(organizationService.getOrganization(testOrgDto.getId())).thenReturn(testOrgDto);
			mockMvc.perform(get(ENDPOINT + "{id}?flatten=true", testOrg.getId()))
					.andExpect(status().isOk())
					.andExpect(result -> assertEquals(2, OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), OrganizationDto.class).getMembers().size()))
					.andExpect(result -> assertEquals(1, OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), OrganizationDto.class).getSubordinateOrganizations().size()));

		}

		@Test
		void testCustomizationOrgReturn() throws Exception {
			OrganizationDto newOrg = new OrganizationDto();
			newOrg.setId(UUID.randomUUID());
			newOrg.setName("test org Child");

			testOrgDto.setSubOrgsUUID(Set.of(newOrg.getId()));

			Person p = Person.builder()
					.id(UUID.randomUUID())
					.firstName("Donny")
					.middleName("Dont")
					.lastName("Does")
					.build();
			Person p2 = Person.builder()
					.id(UUID.randomUUID())
					.firstName("John")
					.middleName("Q")
					.lastName("Public")
					.build();

			newOrg.setMembersUUID(Set.of(p.getId(), p2.getId()));

			Mockito.when(organizationService.getOrganization(newOrg.getId())).thenReturn(newOrg);
			Mockito.when(organizationService.getOrganization(testOrgDto.getId())).thenReturn(testOrgDto);

			// mock out the customize entity - we're not testing that it "works" here, rather the code path
			//  thru it is OK in the controller... so return any JsonNode.
			Mockito.when(organizationService.customizeEntity(Mockito.anyMap(), Mockito.any(OrganizationDto.class)))
					.thenReturn(OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(newOrg)));

			mockMvc.perform(get(ENDPOINT + "?people=id,firstName&organizations=id,name"))
					.andExpect(status().isOk());

			mockMvc.perform(get(ENDPOINT + "?people=id,firstName&organizations=id,name"))
					.andExpect(status().isOk());

			// ...get a single ORG, and do the motions again...

			// but this time make sure the controller populated the options map correctly
			Mockito.when(organizationService.customizeEntity(Mockito.anyMap(), Mockito.any(OrganizationDto.class)))
					.thenAnswer(invocationOnMock -> {
						Map<String, String> map = invocationOnMock.getArgument(0);
						assertEquals("id,firstName", map.get("people"));
						assertEquals("id,name", map.get("organizations"));
						return OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(newOrg));
					});

			// flatten, with customization
			mockMvc.perform(get(ENDPOINT + "{id}?flatten=true&people=id,firstName&organizations=id,name", testOrg.getId()))
					.andExpect(status().isOk());

			// no flatten, with customization
			mockMvc.perform(get(ENDPOINT + "{id}?flatten=false&people=id,firstName&organizations=id,name", testOrg.getId()))
					.andExpect(status().isOk());

		}
	}
}

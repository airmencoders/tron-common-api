package mil.tron.commonapi.integration;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import mil.tron.commonapi.dto.OrganizationDto;
import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.dto.PrivilegeDto;
import mil.tron.commonapi.dto.PrivilegeDtoResponseWrapper;
import mil.tron.commonapi.dto.appclient.AppClientUserDto;
import mil.tron.commonapi.dto.appclient.AppClientUserDtoResponseWrapped;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.PersonRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.transaction.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = { "security.enabled=true", "liquibase.enabled=true" })
@TestPropertySource(locations = "classpath:application-test.properties")
@ActiveProfiles(value = { "development", "test" })
@AutoConfigureMockMvc
public class EntityFieldAuthIntegrationTests {

    private static final String XFCC_HEADER_NAME = "x-forwarded-client-cert";
    private static final String AUTH_HEADER_NAME = "authorization";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private DashboardUser adminUser;
    private UUID adminId = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppClientUserRespository appClientUserRespository;

    @Autowired
    private DashboardUserRepository dashboardUserRepository;

    @Autowired
    private PrivilegeRepository privilegeRepository;

    @Autowired
    private PersonRepository personRepository;

    private PersonDto personDto = PersonDto
            .builder()
            .firstName("Homer")
            .lastName("Simpson")
            .id(UUID.randomUUID())
            .email("test@test.com")
            .build();

    private OrganizationDto organizationDto = OrganizationDto
            .builder()
            .id(UUID.randomUUID())
            .name("TestOrg")
            .build();

    @BeforeEach
    void setup() {

        // add the dashboard admin
        // create the admin
        adminUser = DashboardUser.builder()
                .id(adminId)
                .email("admin@tester.com")
                .privileges(Set.of(
                        privilegeRepository.findByName("DASHBOARD_ADMIN").orElseThrow(() -> new RecordNotFoundException("No DASH_BOARD ADMIN")),
                        privilegeRepository.findByName("DASHBOARD_USER").orElseThrow(() -> new RecordNotFoundException("No DASH_BOARD USER"))))
                .build();

        dashboardUserRepository.save(adminUser);

        // make the "NewApp" app client
        appClientUserRespository.saveAndFlush(AppClientUser.builder()
                .name("NewApp")
                .privileges(new HashSet<>())
                .appClientDevelopers(new HashSet<>())
                .build());
    }

    @AfterEach
    void reset() {
        // remove "NewApp"
        appClientUserRespository
                .findByNameIgnoreCase("NewApp")
                .ifPresent(client -> appClientUserRespository.delete(client));

        // clean up users
        dashboardUserRepository.deleteById(adminId);
    }

    @Test
    @Transactional
    @Rollback
    void testPrivsRead() throws Exception {

        // NewApp doesn't have PERSON_READ rights
        mockMvc.perform(get("/v2/person")
                .header(XFCC_HEADER_NAME, generateXfccHeader("NewApp")))
                .andExpect(status().isForbidden());

        // NewApp doesn't have ORG_READ rights
        mockMvc.perform(get("/v2/organization")
                .header(XFCC_HEADER_NAME, generateXfccHeader("NewApp")))
                .andExpect(status().isForbidden());

        // dashboard admin can still do what they want
        mockMvc.perform(get("/v2/person")
                .header(AUTH_HEADER_NAME, createToken(adminUser.getEmail()))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO()))
                .andExpect(status().isOk());


    }

    @Test
    @Transactional
    @Rollback
    void testPrivsCreate() throws Exception {

        // NewApp doesn't have PERSON_CREATE rights
        mockMvc.perform(post("/v2/person")
                .header(XFCC_HEADER_NAME, generateXfccHeader("NewApp"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(personDto)))
                .andExpect(status().isForbidden());

        // NewApp doesn't have ORG_CREATE rights
        mockMvc.perform(post("/v2/organization")
                .header(XFCC_HEADER_NAME, generateXfccHeader("NewApp"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(organizationDto)))
                .andExpect(status().isForbidden());

        // give NewApp privs
        AppClientUser user = appClientUserRespository.findByNameIgnoreCase("NewApp")
                .orElseThrow(() -> new RecordNotFoundException("Can't find app client"));

        Privilege personCreate = privilegeRepository.findByName("PERSON_CREATE")
                .orElseThrow(() -> new RecordNotFoundException("Can't find privilege"));

        Privilege orgCreate = privilegeRepository.findByName("ORGANIZATION_CREATE")
                .orElseThrow(() -> new RecordNotFoundException("Can't find privilege"));

        user.setPrivileges(Sets.newHashSet(personCreate, orgCreate));
        appClientUserRespository.saveAndFlush(user);

        mockMvc.perform(post("/v2/person")
                .header(XFCC_HEADER_NAME, generateXfccHeader("NewApp"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(personDto)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v2/organization")
                .header(XFCC_HEADER_NAME, generateXfccHeader("NewApp"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(organizationDto)))
                .andExpect(status().isCreated());

    }

    @Test
    @Transactional
    @Rollback
    void testPrivsDelete() throws Exception {

        mockMvc.perform(post("/v2/person")
                .header(AUTH_HEADER_NAME, createToken(adminUser.getEmail()))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(personDto)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v2/organization")
                .header(AUTH_HEADER_NAME, createToken(adminUser.getEmail()))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(organizationDto)))
                .andExpect(status().isCreated());

        // NewApp doesn't have PERSON_DELETE rights
        mockMvc.perform(delete("/v2/person/{id}", personDto.getId())
                .header(XFCC_HEADER_NAME, generateXfccHeader("NewApp")))
                .andExpect(status().isForbidden());

        // NewApp doesn't have ORG_DELETE rights
        mockMvc.perform(delete("/v2/organization/{id}", organizationDto.getId())
                .header(XFCC_HEADER_NAME, generateXfccHeader("NewApp")))
                .andExpect(status().isForbidden());

        // give NewApp privs
        AppClientUser user = appClientUserRespository.findByNameIgnoreCase("NewApp")
                .orElseThrow(() -> new RecordNotFoundException("Can't find app client"));

        Privilege personDelete = privilegeRepository.findByName("PERSON_DELETE")
                .orElseThrow(() -> new RecordNotFoundException("Can't find privilege"));

        Privilege orgDelete = privilegeRepository.findByName("ORGANIZATION_DELETE")
                .orElseThrow(() -> new RecordNotFoundException("Can't find privilege"));

        user.setPrivileges(Sets.newHashSet(personDelete, orgDelete));
        appClientUserRespository.saveAndFlush(user);

        mockMvc.perform(delete("/v2/person/{id}", personDto.getId())
                .header(XFCC_HEADER_NAME, generateXfccHeader("NewApp")))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/v2/organization/{id}", organizationDto.getId())
                .header(XFCC_HEADER_NAME, generateXfccHeader("NewApp")))
                .andExpect(status().isNoContent());


    }

    @Test
    @Transactional
    @Rollback
    void testPrivsEdit() throws Exception {

        mockMvc.perform(post("/v2/person")
                .header(AUTH_HEADER_NAME, createToken(adminUser.getEmail()))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(personDto)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v2/organization")
                .header(AUTH_HEADER_NAME, createToken(adminUser.getEmail()))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(organizationDto)))
                .andExpect(status().isCreated());

        // NewApp doesn't have PERSON_EDIT rights
        mockMvc.perform(put("/v2/person/{id}", personDto.getId())
                .header(XFCC_HEADER_NAME, generateXfccHeader("NewApp"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(personDto)))
                .andExpect(status().isForbidden());

        // NewApp doesn't have ORG_EDIT rights
        mockMvc.perform(put("/v2/organization/{id}", organizationDto.getId())
                .header(XFCC_HEADER_NAME, generateXfccHeader("NewApp"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(organizationDto)))
                .andExpect(status().isForbidden());

        personDto.setRank("Capt");
        personDto.setBranch(Branch.USAF);

        // give EDIT rights
        AppClientUser user = appClientUserRespository.findByNameIgnoreCase("NewApp")
                .orElseThrow(() -> new RecordNotFoundException("Can't find app client"));

        Privilege personEdit = privilegeRepository.findByName("PERSON_EDIT")
                .orElseThrow(() -> new RecordNotFoundException("Can't find privilege"));

        Privilege orgEdit = privilegeRepository.findByName("ORGANIZATION_EDIT")
                .orElseThrow(() -> new RecordNotFoundException("Can't find privilege"));

        user.setPrivileges(Sets.newHashSet(personEdit, orgEdit));
        appClientUserRespository.saveAndFlush(user);

        // Person edit allowed now (albeit no access to any of the protected fields)
        mockMvc.perform(put("/v2/person/{id}", personDto.getId())
                .header(XFCC_HEADER_NAME, generateXfccHeader("NewApp"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(personDto)))
                .andExpect(status().isNonAuthoritativeInformation())
                .andExpect(header().string("Warning", containsString("rank")))
                .andExpect(header().string("Warning", containsString("214")))
                .andExpect(jsonPath("$.rank", equalTo("Unk")));

        // Org edit allowed now (albeit no access to any of the protected fields)
        mockMvc.perform(put("/v2/organization/{id}", organizationDto.getId())
                .header(XFCC_HEADER_NAME, generateXfccHeader("NewApp"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(organizationDto)))
                .andExpect(status().isOk());

        // add some field-level permissions, get the App Client record
        MvcResult appClient = mockMvc.perform(get("/v2/app-client")
                .header(AUTH_HEADER_NAME, createToken(adminUser.getEmail()))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult privsList = mockMvc.perform(get("/v2/app-client/privs")
                .header(AUTH_HEADER_NAME, createToken(adminUser.getEmail()))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO()))
                .andExpect(status().isOk())
                .andReturn();

        // get all the privs we can assign to an app client
        List<PrivilegeDto> clientPrivs = OBJECT_MAPPER.readValue(privsList
                .getResponse()
                .getContentAsString(),
                PrivilegeDtoResponseWrapper.class).getData();


        List<AppClientUserDto> appList = OBJECT_MAPPER.readValue(appClient
                .getResponse()
                .getContentAsString(),
                AppClientUserDtoResponseWrapped.class).getData();

        // find New App's record
        AppClientUserDto appRecord = appList
                .stream()
                .filter(item -> item.getName().equals("NewApp"))
                .collect(Collectors.toList())
                .get(0);

        List<PrivilegeDto> privs = appRecord.getPrivileges();
        privs.add(clientPrivs
                        .stream()
                        .filter(item -> item.getName().endsWith("-rank"))
                        .findFirst().orElseThrow(() -> new RecordNotFoundException("NO rank priv found")));
        privs.add(clientPrivs
                        .stream()
                        .filter(item -> item.getName().endsWith("-lastName"))
                        .findFirst().orElseThrow(() -> new RecordNotFoundException("NO lastName priv found")));
        privs.add(clientPrivs
                .stream()
                .filter(item -> item.getName().endsWith("-leader"))
                .findFirst().orElseThrow(() -> new RecordNotFoundException("NO leader priv found")));

        appRecord.setPrivileges(privs);

        mockMvc.perform(put("/v2/app-client/{id}", appRecord.getId())
                .header(AUTH_HEADER_NAME, createToken(adminUser.getEmail()))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(appRecord)))
                .andExpect(status().isOk());

        // try to set rank again on that same person, since we have the privs to modify that field now
        mockMvc.perform(put("/v2/person/{id}", personDto.getId())
                .header(XFCC_HEADER_NAME, generateXfccHeader("NewApp"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(personDto)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Warning"));

        // try to change the firstName thru json patch - should get a 203 with firstName
        //  since we tried to change that and we didn't have the privileges
        JSONObject content = new JSONObject();
        content.put("op","replace");
        content.put("path","/firstName");
        content.put("value", "George");
        JSONArray contentArr = new JSONArray();
        contentArr.put(content);
        mockMvc.perform(patch("/v2/person/{id}", personDto.getId())
                .header(XFCC_HEADER_NAME, generateXfccHeader("NewApp"))
                .contentType("application/json-patch+json")
                .content(contentArr.toString()))
                .andExpect(status().isNonAuthoritativeInformation())
                .andExpect(header().string("Warning", endsWith("firstName")))
                .andExpect(header().string("Warning", containsString("214")))
                .andExpect(jsonPath("$.firstName", equalTo("Homer")));

        // try to change the lastName thru json patch - should NOT get a 203
        content = new JSONObject();
        content.put("op","replace");
        content.put("path","/lastName");
        content.put("value", "Smithers");
        contentArr = new JSONArray();
        contentArr.put(content);
        mockMvc.perform(patch("/v2/person/{id}", personDto.getId())
                .header(XFCC_HEADER_NAME, generateXfccHeader("NewApp"))
                .contentType("application/json-patch+json")
                .content(contentArr.toString()))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Warning"))
                .andExpect(jsonPath("$.lastName", equalTo("Smithers")));


        UUID leaderId = UUID.randomUUID();
        personRepository.save(Person
                .builder()
                .id(leaderId)
                .firstName("Monty")
                .lastName("Burns")
                .email("mb@springfield.gov")
                .build());

        // try to change the leader of Org thru json patch - should NOT get a 203
        content = new JSONObject();
        content.put("op","replace");
        content.put("path","/leader");
        content.put("value", leaderId.toString());
        contentArr = new JSONArray();
        contentArr.put(content);
        mockMvc.perform(patch("/v2/organization/{id}", organizationDto.getId())
                .header(XFCC_HEADER_NAME, generateXfccHeader("NewApp"))
                .contentType("application/json-patch+json")
                .content(contentArr.toString()))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Warning"))
                .andExpect(jsonPath("$.leader", equalTo(leaderId.toString())));

        content = new JSONObject();
        content.put("op","replace");
        content.put("path","/name");
        content.put("value", "Springfield Nuclear");
        contentArr = new JSONArray();
        contentArr.put(content);
        mockMvc.perform(patch("/v2/organization/{id}", organizationDto.getId())
                .header(XFCC_HEADER_NAME, generateXfccHeader("NewApp"))
                .contentType("application/json-patch+json")
                .content(contentArr.toString()))
                .andExpect(status().isNonAuthoritativeInformation())
                .andExpect(header().string("Warning", endsWith("name")))
                .andExpect(header().string("Warning", containsString("214")))
                .andExpect(jsonPath("$.name", equalTo(organizationDto.getName())));
    }
    
    @Test
    @Transactional
    @Rollback
    void testUserCanEditOwnData() throws Exception {
    	var dashboardUserOnly = DashboardUser.builder()
                .id(UUID.randomUUID())
                .email("dashboard@user.com")
                .privileges(Set.of(
                        privilegeRepository.findByName("DASHBOARD_USER").orElseThrow(() -> new RecordNotFoundException("No DASH_BOARD USER"))))
                .build();

        dashboardUserRepository.save(dashboardUserOnly);
        
        var dashboardPerson = PersonDto.builder()
        		.id(UUID.randomUUID())
        		.firstName("dashboard")
        		.lastName("user")
        		.email(dashboardUserOnly.getEmail())
        		.rank("Capt")
        		.branch(Branch.USAF)
        		.build();
        
        mockMvc.perform(post("/v2/person")
                .header(AUTH_HEADER_NAME, createToken(adminUser.getEmail()))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(dashboardPerson)))
                .andExpect(status().isCreated());
        
        var newPerson = PersonDto.builder()
        		.id(dashboardPerson.getId())
        		.firstName("new dashboard")
        		.lastName("new user")
        		.email(dashboardUserOnly.getEmail())
        		.build();
        
        mockMvc.perform(put("/v2/person/self/{id}", newPerson.getId())
                .header(AUTH_HEADER_NAME, createToken(dashboardUserOnly.getEmail()))
                .header(XFCC_HEADER_NAME, generateXfccHeaderFromSSO())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(newPerson)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dodid", equalTo(null)));
    }

    String generateXfccHeader(String namespace) {
        String XFCC_BY = "By=spiffe://cluster/ns/" + namespace + "/sa/default";
        String XFCC_H = "FAKE_H=12345";
        String XFCC_SUBJECT = "Subject=\\\"\\\";";
        return new StringBuilder()
                .append(XFCC_BY)
                .append(XFCC_H)
                .append(XFCC_SUBJECT)
                .append("URI=spiffe://cluster.local/ns/" + namespace + "/sa/default")
                .toString();
    }

    /**
     * Helper to generate a XFCC header from the istio gateway
     * @return
     */
    String generateXfccHeaderFromSSO() {
        return generateXfccHeader("istio-system");
    }

    /**
     * Private helper to create a JWT on the fly
     * @param email email to embed with the "email" claim
     * @return the bearer token
     */
    String createToken(String email) {
        Algorithm algorithm = Algorithm.HMAC256("secret");
        return "Bearer " + JWT.create()
                .withIssuer("istio")
                .withClaim("email", email)
                .sign(algorithm);
    }

}

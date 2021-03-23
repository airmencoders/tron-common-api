package mil.tron.commonapi.controller.puckboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import mil.tron.commonapi.service.puckboard.PuckboardExtractorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
@AutoConfigureMockMvc
public class PuckboardEtlControllerTests {
    private static final String ENDPOINT = "/v1/puckboard/extract";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    PuckboardExtractorService service;

    @Autowired
    @Qualifier("puckboardFetcher")
    private RestTemplate restTemplate;

    private MockRestServiceServer puckboardServer;
    private String branchesData;
    private String organizationsData;
    private String personnelData;

    @BeforeEach
    void setupMockPuckboard() throws Exception {
        branchesData = Resources.toString(Resources.getResource("puckboard/mock-branches.json"), StandardCharsets.UTF_8);
        organizationsData = Resources.toString(Resources.getResource("puckboard/mock-organizations.json"), StandardCharsets.UTF_8);
        personnelData = Resources.toString(Resources.getResource("puckboard/mock-personnel.json"), StandardCharsets.UTF_8);
        puckboardServer = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
    }

    @Test
    void testResponse() throws Exception {

        puckboardServer.expect(manyTimes(), requestTo(endsWith("/branch"))).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(branchesData, MediaType.APPLICATION_JSON));

        puckboardServer.expect(manyTimes(), requestTo(endsWith("/organizations?isSchedulingUnit=true"))).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(organizationsData, MediaType.APPLICATION_JSON));

        puckboardServer.expect(manyTimes(), requestTo(endsWith("/personnel"))).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(personnelData, MediaType.APPLICATION_JSON));

        // return empty map for mock
        Mockito.when(service.persistOrgsAndMembers(
                Mockito.any(JsonNode.class),
                Mockito.any(JsonNode.class),
                Mockito.any(JsonNode.class)))
            .thenReturn(new ImmutableMap
                    .Builder<String, Map<UUID, String>>()
                    .put("orgs", new ImmutableMap
                            .Builder<UUID, String>()
                            .build())
                    .build());

        mockMvc.perform(get(ENDPOINT)).andExpect(status().isOk());
    }

    @Test
    void testOrganizationsFailuresOccur() throws Exception {
        puckboardServer.expect(manyTimes(), requestTo(endsWith("/organizations?isSchedulingUnit=true")))  // mock the Rest call to /organizations fails
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isInternalServerError());


    }

    @Test
    void testPersonnelFailuresOccur() throws Exception {
        puckboardServer.expect(manyTimes(), requestTo(endsWith("/organizations?isSchedulingUnit=true"))).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(organizationsData, MediaType.APPLICATION_JSON));

        puckboardServer.expect(manyTimes(), requestTo(endsWith("/personnel")))  // mock the Rest call to /personnel fails
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isInternalServerError());

    }

    @Test
    void testBranchFailuresOccur() throws Exception {
        puckboardServer.expect(manyTimes(), requestTo(endsWith("/organizations?isSchedulingUnit=true"))).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(organizationsData, MediaType.APPLICATION_JSON));

        puckboardServer.expect(manyTimes(), requestTo(endsWith("/personnel"))).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(personnelData, MediaType.APPLICATION_JSON));

        puckboardServer.expect(manyTimes(), requestTo(endsWith("/branch")))  // mock the Rest call to /branch fails
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isInternalServerError());

    }
}

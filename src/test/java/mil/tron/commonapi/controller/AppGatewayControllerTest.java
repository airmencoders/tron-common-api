package mil.tron.commonapi.controller;

import mil.tron.commonapi.appgateway.AppSourceEndpointsBuilder;
import mil.tron.commonapi.appgateway.AppSourceInterfaceDefinition;
import mil.tron.commonapi.service.AppGatewayService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.InputStreamResource;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.servlet.http.HttpServletRequest;
import javax.transaction.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.mockito.internal.util.MockUtil.createMock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties")
public class AppGatewayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppSourceEndpointsBuilder appSourceEndpointsBuilder;

    @MockBean
    private AppGatewayService appGatewayService;

    @Transactional
    @Rollback
    @Test
    void testHandleGetRequests() throws Exception {
        HttpServletRequest mockRequest = Mockito.mock(HttpServletRequest.class);
        InputStreamResource mockResult = new InputStreamResource(
                new ByteArrayInputStream("Result".getBytes(StandardCharsets.UTF_8))
        );
        Mockito.when(appGatewayService.sendRequestToAppSource(mockRequest))
                .thenReturn(mockResult);

        this.appSourceEndpointsBuilder.initializeWithAppSourceDef(
                new AppSourceInterfaceDefinition("Name", "mock.yml",
                        "http:////localhost", "mock"));

        mockMvc.perform(get("/v1/app/mock/test"))
                .andExpect(status().isOk());
    }
}

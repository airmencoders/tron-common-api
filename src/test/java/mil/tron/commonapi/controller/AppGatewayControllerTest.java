package mil.tron.commonapi.controller;

import mil.tron.commonapi.appgateway.AppSourceEndpointsBuilder;
import mil.tron.commonapi.appgateway.AppSourceInterfaceDefinition;
import mil.tron.commonapi.service.AppGatewayService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.servlet.http.HttpServletRequest;

import static org.mockito.ArgumentMatchers.any;
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

    @Test
    void testHandleGetRequests() throws Exception {
        AppSourceInterfaceDefinition appDef = new AppSourceInterfaceDefinition("Name", "mock.yml",
                "http:////localhost", "mock");
        byte[] mockResult = "result".getBytes();
        Mockito.when(appGatewayService.sendRequestToAppSource(any(HttpServletRequest.class)))
                .thenReturn(mockResult);
        Mockito.when(appGatewayService.addSourceDefMapping("mock", appDef))
                .thenReturn(true);

        this.appSourceEndpointsBuilder.initializeWithAppSourceDef(appDef);

        mockMvc.perform(get("/v1/app/mock/test"))
                .andExpect(status().isOk());
    }

    @Test
    void testHandleNullResponse() throws Exception {
        AppSourceInterfaceDefinition appDef = new AppSourceInterfaceDefinition("Name", "mock.yml",
                "http:////localhost", "mock-fail");
        Mockito.when(appGatewayService.sendRequestToAppSource(any(HttpServletRequest.class)))
                .thenReturn(null);

        Mockito.when(appGatewayService.addSourceDefMapping("mock-fail", appDef))
                .thenReturn(true);
        this.appSourceEndpointsBuilder.initializeWithAppSourceDef(appDef);

        mockMvc.perform(get("/v1/app/mock-fail/test"))
                .andExpect(status().is(204));
    }
}

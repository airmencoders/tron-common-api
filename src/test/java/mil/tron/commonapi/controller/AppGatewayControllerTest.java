package mil.tron.commonapi.controller;

import mil.tron.commonapi.appgateway.AppSourceConfig;
import mil.tron.commonapi.appgateway.AppSourceEndpointsBuilder;
import mil.tron.commonapi.appgateway.AppSourceInterfaceDefinition;
import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.service.AppGatewayService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.servlet.http.HttpServletRequest;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    
    @MockBean
    private AppSourceConfig appSourceConfig;

    @Test
    @WithMockUser(username = "guardianangel", authorities = "mock/test")
    void testHandleGetRequests() throws Exception {
        AppSourceInterfaceDefinition appDef = new AppSourceInterfaceDefinition("Name", "mock.yml",
                "http:////localhost", "mock");
        AppSource appSource = AppSource.builder()
                .name(appDef.getName())
                .openApiSpecFilename(appDef.getOpenApiSpecFilename())
                .appSourcePath(appDef.getAppSourcePath())
                .build();

        byte[] mockResult = "result".getBytes();
        Mockito.when(appGatewayService.sendRequestToAppSource(any(HttpServletRequest.class)))
                .thenReturn(mockResult);
        Mockito.when(appSourceConfig.addAppSourcePathToDefMapping("mock", appDef))
                .thenReturn(true);

        this.appSourceEndpointsBuilder.initializeWithAppSourceDef(appDef, appSource);

        mockMvc.perform(get("/v1/app/mock/test"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/app/mock/test30"))
                .andExpect(status().isNotFound());
    }
    
    @Test
    @WithMockUser(username = "guardianangel", authorities = "mock/test-post")
    void testHandlePostRequests() throws Exception {
        AppSourceInterfaceDefinition appDef = new AppSourceInterfaceDefinition("Name", "mock.yml",
                "http:////localhost", "mock");
        AppSource appSource = AppSource.builder()
                .name(appDef.getName())
                .openApiSpecFilename(appDef.getOpenApiSpecFilename())
                .appSourcePath(appDef.getAppSourcePath())
                .build();

        byte[] mockResult = "result".getBytes();
        
        Mockito.when(appGatewayService.sendRequestToAppSource(any(HttpServletRequest.class)))
                .thenReturn(mockResult);
        Mockito.when(appSourceConfig.addAppSourcePathToDefMapping("mock-post", appDef))
                .thenReturn(true);

        this.appSourceEndpointsBuilder.initializeWithAppSourceDef(appDef, appSource);

        mockMvc.perform(post("/v1/app/mock/test-post"))
                .andExpect(status().isOk());
    }
    
    @Test
    @WithMockUser(username = "guardianangel", authorities = "mock/test-delete")
    void testHandleDeleteRequests() throws Exception {
        AppSourceInterfaceDefinition appDef = new AppSourceInterfaceDefinition("Name", "mock.yml",
                "http:////localhost", "mock");
        AppSource appSource = AppSource.builder()
                .name(appDef.getName())
                .openApiSpecFilename(appDef.getOpenApiSpecFilename())
                .appSourcePath(appDef.getAppSourcePath())
                .build();

        byte[] mockResult = "result".getBytes();
        
        Mockito.when(appGatewayService.sendRequestToAppSource(any(HttpServletRequest.class)))
                .thenReturn(mockResult);
        Mockito.when(appSourceConfig.addAppSourcePathToDefMapping("mock-delete", appDef))
                .thenReturn(true);

        this.appSourceEndpointsBuilder.initializeWithAppSourceDef(appDef, appSource);

        mockMvc.perform(delete("/v1/app/mock/test-delete"))
                .andExpect(status().isOk());
    }
    
    @Test
    @WithMockUser(username = "guardianangel", authorities = "mock/test-put")
    void testHandlePutRequests() throws Exception {
        AppSourceInterfaceDefinition appDef = new AppSourceInterfaceDefinition("Name", "mock.yml",
                "http:////localhost", "mock");
        AppSource appSource = AppSource.builder()
                .name(appDef.getName())
                .openApiSpecFilename(appDef.getOpenApiSpecFilename())
                .appSourcePath(appDef.getAppSourcePath())
                .build();

        byte[] mockResult = "result".getBytes();
        
        Mockito.when(appGatewayService.sendRequestToAppSource(any(HttpServletRequest.class)))
                .thenReturn(mockResult);
        Mockito.when(appSourceConfig.addAppSourcePathToDefMapping("mock-put", appDef))
                .thenReturn(true);

        this.appSourceEndpointsBuilder.initializeWithAppSourceDef(appDef, appSource);

        mockMvc.perform(put("/v1/app/mock/test-put"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "guardianangel", authorities = "mock-fail/test")
    void testHandleNullResponse() throws Exception {
        AppSourceInterfaceDefinition appDef = new AppSourceInterfaceDefinition("Name", "mock.yml",
                "http:////localhost", "mock-fail");
        AppSource appSource = AppSource.builder()
                .name(appDef.getName())
                .openApiSpecFilename(appDef.getOpenApiSpecFilename())
                .appSourcePath(appDef.getAppSourcePath())
                .build();

        Mockito.when(appGatewayService.sendRequestToAppSource(any(HttpServletRequest.class)))
                .thenReturn(null);

        Mockito.when(appSourceConfig.addAppSourcePathToDefMapping("mock-fail", appDef))
                .thenReturn(true);
        this.appSourceEndpointsBuilder.initializeWithAppSourceDef(appDef, appSource);

        mockMvc.perform(get("/v1/app/mock-fail/test"))
                .andExpect(status().is(204));
    }
}

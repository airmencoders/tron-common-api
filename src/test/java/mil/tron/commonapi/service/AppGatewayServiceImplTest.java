package mil.tron.commonapi.service;

import mil.tron.commonapi.appgateway.AppGatewayRouteBuilder;
import mil.tron.commonapi.appgateway.AppSourceConfig;
import mil.tron.commonapi.appgateway.AppSourceInterfaceDefinition;
import mil.tron.commonapi.dto.appsource.AppSourceDetailsDto;
import mil.tron.commonapi.entity.appsource.AppSource;

import org.apache.camel.*;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringBootRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.apache.camel.builder.RouteBuilder;

import javax.servlet.http.HttpServletRequest;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(CamelSpringBootRunner.class)
@ExtendWith(MockitoExtension.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
class AppGatewayServiceImplTest {
	private static final String APP_SOURCE_PATH = "mock";
	private static final String GATEWAY_ENDPOINT_URI = AppGatewayRouteBuilder.generateAppSourceRouteUri(APP_SOURCE_PATH);
	private static final String GATEWAY_ID = AppGatewayRouteBuilder.generateAppSourceRouteId(APP_SOURCE_PATH);

	@Autowired
	private AppGatewayRouteBuilder appGatewayRouteBuilder;
	
	@MockBean
	private AppSourceService appSourceService;
	
	@MockBean
	private AppSourceConfig appSourceConfig;
	
    private AppGatewayServiceImpl appGatewayService;

    private CamelContext context;

    @Autowired
    AppGatewayServiceImplTest(CamelContext context, AppGatewayServiceImpl appGatewayService) throws Exception {
        this.context = context;
        this.appGatewayService = appGatewayService;
    }

    @BeforeEach
    void beforeEach() throws Exception {
    	appGatewayRouteBuilder.createGatewayRoute(APP_SOURCE_PATH);
    	
    	RouteBuilder mockRouteBuilder = new RouteBuilder(this.context) {
            @Override
            public void configure() throws Exception {
                AdviceWithRouteBuilder.adviceWith(this.getContext(), GATEWAY_ID,
                        endpoint -> endpoint.replaceFromWith(GATEWAY_ENDPOINT_URI + "Stub"));
                from(GATEWAY_ENDPOINT_URI)
                        .id(GATEWAY_ID + "Mock")
                        .to("mock:" + GATEWAY_ENDPOINT_URI);
            }
        };
        this.context.addRoutes(mockRouteBuilder);
    }

    @AfterEach
    void afterEach() throws Exception {
        this.context.removeRoute(GATEWAY_ID + "Mock");
    }

    @Test
    void testBuildPathForAppSource() {
        String testUriRequest = "/api/v1/app/the-app-source/the-path/11";
        AppGatewayServiceImpl appGatewayService = new AppGatewayServiceImpl(this.context.createFluentProducerTemplate(), appSourceService, appSourceConfig);
        String appSource = appGatewayService.buildPathForAppSource(testUriRequest);
        assertThat(appSource).isEqualTo("/the-path/11");
    }

    @Test
    void testBuildAppPath() {
        String testUriRequest = "/api/v1/app/the-app-source/the-path/11";
        AppGatewayServiceImpl appGatewayService = new AppGatewayServiceImpl(this.context.createFluentProducerTemplate(), appSourceService, appSourceConfig);
        String appSource = appGatewayService.buildAppPath(testUriRequest);
        assertThat(appSource).isEqualTo("the-app-source");
    }

    @Test
    void testSendRequestToAppSource() throws Exception {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:" +
        		GATEWAY_ENDPOINT_URI, MockEndpoint.class);

        mockEndpoint.returnReplyBody(new Expression() {
            @Override
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                return (T) new ByteArrayInputStream("[{\"fieldKey\": \"value\"]".getBytes());
            }
        });

        HttpServletRequest mockRequest = Mockito.mock(HttpServletRequest.class);
        Mockito.when(mockRequest.getMethod()).thenReturn("GET");
        Mockito.when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader("Test Body")));
        Mockito.when(mockRequest.getRequestURI()).thenReturn("/api/v1/app/mock/mock-request");
        AppSourceInterfaceDefinition appSourceDef = new AppSourceInterfaceDefinition("Mock", "mock.yml", "localhost", "mock");
        Mockito.when(this.appSourceConfig.getPathToDefinitionMap()).thenReturn(Map.of("mock", appSourceDef));
        Mockito.when(this.appSourceConfig.getAppSourceDefs()).thenReturn(Map.of(appSourceDef, AppSource.builder()
        		.throttleEnabled(true)
        		.throttleRequestCount(1L)
        		.build()));
        
        Mockito.when(appSourceService.getAppSource(Mockito.any())).thenReturn(AppSourceDetailsDto.builder()
        		.throttleRequestCount(1L)
        		.throttleEnabled(true)
        		.build());
        
        byte[] result = this.appGatewayService.sendRequestToAppSource(mockRequest);
        assertThat(result).isNotNull();
        
        Mockito.when(mockRequest.getMethod()).thenReturn("POST");
        
        result = this.appGatewayService.sendRequestToAppSource(mockRequest);
        assertThat(result).isNotNull();
        
        // Test that communication errors to app source throws 503
        mockEndpoint.whenAnyExchangeReceived(new Processor() {
			@Override
			public void process(Exchange exchange) throws Exception {
				throw new Exception("fail");
			}
		});
        Mockito.when(mockRequest.getRequestURI()).thenReturn("/api/v1/app/mock/mock-request");
        try {
        	this.appGatewayService.sendRequestToAppSource(mockRequest);
        	Assertions.fail("Request should have thrown exception");
        } catch (ResponseStatusException ex) {
        	assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    @Test
    void testAppDefMapping() {

    }
}

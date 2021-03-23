package mil.tron.commonapi.service;

import mil.tron.commonapi.appgateway.AppGatewayRouteBuilder;
import mil.tron.commonapi.appgateway.AppSourceInterfaceDefinition;
import org.apache.camel.*;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.test.spring.CamelSpringBootRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.apache.camel.builder.RouteBuilder;

import javax.servlet.http.HttpServletRequest;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(CamelSpringBootRunner.class)
@ExtendWith(MockitoExtension.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
class AppGatewayServiceImplTest {

    private AppGatewayServiceImpl appGatewayService;

    private CamelContext context;

    @Autowired
    AppGatewayServiceImplTest(CamelContext context, AppGatewayServiceImpl appGatewayService) throws Exception {
        this.context = context;
        this.appGatewayService = appGatewayService;
    }

    @BeforeEach
    void beforeEach() throws Exception {
        RouteBuilder mockRouteBuilder = new RouteBuilder(this.context) {
            @Override
            public void configure() throws Exception {
                AdviceWithRouteBuilder.adviceWith(this.getContext(), AppGatewayRouteBuilder.APP_GATEWAY_ENDPOINT_ID,
                        endpoint -> endpoint.replaceFromWith(AppGatewayRouteBuilder.APP_GATEWAY_ENDPOINT + "Stub"));
                from(AppGatewayRouteBuilder.APP_GATEWAY_ENDPOINT)
                        .id(AppGatewayRouteBuilder.APP_GATEWAY_ENDPOINT + "Mock")
                        .to("mock:" + AppGatewayRouteBuilder.APP_GATEWAY_ENDPOINT);
            }
        };
        this.context.addRoutes(mockRouteBuilder);
    }

    @AfterEach
    void afterEach() throws Exception {
        this.context.removeRoute(AppGatewayRouteBuilder.APP_GATEWAY_ENDPOINT + "Mock");
    }

    @Test
    void testBuildPathForAppSource() {
        String testUriRequest = "/api/v1/app/the-app-source/the-path/11";
        AppGatewayServiceImpl appGatewayService = new AppGatewayServiceImpl(this.context.createFluentProducerTemplate());
        String appSource = appGatewayService.buildPathForAppSource(testUriRequest);
        assertThat(appSource).isEqualTo("/the-path/11");
    }

    @Test
    void testBuildAppPath() {
        String testUriRequest = "/api/v1/app/the-app-source/the-path/11";
        AppGatewayServiceImpl appGatewayService = new AppGatewayServiceImpl(this.context.createFluentProducerTemplate());
        String appSource = appGatewayService.buildAppPath(testUriRequest);
        assertThat(appSource).isEqualTo("the-app-source");
    }

    @Test
    void testSendRequestToAppSource() throws Exception {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:" +
                AppGatewayRouteBuilder.APP_GATEWAY_ENDPOINT, MockEndpoint.class);

        mockEndpoint.returnReplyBody(new Expression() {
            @Override
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                return (T) new ByteArrayInputStream("[{\"fieldKey\": \"value\"]".getBytes());
            }
        });
        this.appGatewayService.addSourceDefMapping("mock",
                new AppSourceInterfaceDefinition("Mock", "mock.yml",
                        "localhost", "mock"));
        HttpServletRequest mockRequest = Mockito.mock(HttpServletRequest.class);
        Mockito.when(mockRequest.getRequestURI()).thenReturn("/api/v1/app/mock/mock-request");
        byte[] result = this.appGatewayService.sendRequestToAppSource(mockRequest);
        assertThat(result).isNotNull();
    }

    @Test
    void testAppDefMapping() {

    }
}

package mil.tron.commonapi.controller;

import liquibase.pro.packaged.A;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(AppGatewayController.class)
public class AppGatewayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testBuildPathForAppSource() {
        String testUriRequest = "/api/v1/app/the-app-source/the-path/11";
        AppGatewayController appGatewayController = new AppGatewayController();
        String appSource = appGatewayController.buildPathForAppSource(testUriRequest);
        assertThat(appSource).isEqualTo("/the-path/11");
    }

    @Test
    void testBuildAppPath() {
        String testUriRequest = "/api/v1/app/the-app-source/the-path/11";
        AppGatewayController appGatewayController = new AppGatewayController();
        String appSource = appGatewayController.buildAppPath(testUriRequest);
        assertThat(appSource).isEqualTo("the-app-source");
    }

    @Test
    void testHandleGetRequests() {

    }
}

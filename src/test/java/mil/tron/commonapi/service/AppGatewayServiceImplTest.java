package mil.tron.commonapi.service;

import mil.tron.commonapi.controller.AppGatewayController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;


@ExtendWith(MockitoExtension.class)
class AppGatewayServiceImplTest {

    @Test
    void testBuildPathForAppSource() {
        String testUriRequest = "/api/v1/app/the-app-source/the-path/11";
        AppGatewayServiceImpl appGatewayService = new AppGatewayServiceImpl();
        String appSource = appGatewayService.buildPathForAppSource(testUriRequest);
        assertThat(appSource).isEqualTo("/the-path/11");
    }

    @Test
    void testBuildAppPath() {
        String testUriRequest = "/api/v1/app/the-app-source/the-path/11";
        AppGatewayServiceImpl appGatewayService = new AppGatewayServiceImpl();
        String appSource = appGatewayService.buildAppPath(testUriRequest);
        assertThat(appSource).isEqualTo("the-app-source");
    }
}

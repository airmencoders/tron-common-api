package mil.tron.commonapi.appgateway;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Service;

@Service
public class AppGatewayRouteBuilder extends RouteBuilder {
    public static final String APP_GATEWAY_ENDPOINT = "direct:app-gateway";
    public static final String APP_GATEWAY_ENDPOINT_ID = "app-gateway";

    @Override
    public void configure() throws Exception {
        from (AppGatewayRouteBuilder.APP_GATEWAY_ENDPOINT)
	        .id(AppGatewayRouteBuilder.APP_GATEWAY_ENDPOINT_ID)
	        .streamCaching()
	        .toD("${header.request-url}");
    }
}

package mil.tron.commonapi.appgateway;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.stereotype.Service;

@Service
public class AppGatewayRouteBuilder extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        from ("direct:app-gateway")
                .toD("${body}");
//                .log("${body}")
//                .unmarshal().json(JsonLibrary.Gson);
    }
}

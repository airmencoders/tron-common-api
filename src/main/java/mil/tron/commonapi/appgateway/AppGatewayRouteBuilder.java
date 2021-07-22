package mil.tron.commonapi.appgateway;

import org.apache.camel.CamelContext;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AppGatewayRouteBuilder {
	public static final String ROUTE_TYPE = "direct:";
    public static final String APP_GATEWAY_ENDPOINT_ID = "app-gateway";

    private CamelContext camelContext;
    
    public AppGatewayRouteBuilder(CamelContext camelContext) {
    	this.camelContext = camelContext;
    }
    
    public GatewayRoute createGatewayRoute(String appSourcePath) {
    	final GatewayRoute route = new GatewayRoute(camelContext, appSourcePath);
    	try {
			camelContext.addRoutes(route);
		} catch (Exception e) {
			log.warn("Could not create Camel route for the App Source: " + appSourcePath);
		}
    	
    	return route;
    }
    
    public static String generateAppSourceRouteUri(String appSourcePath) {
    	return ROUTE_TYPE + appSourcePath;
    }
    
    public static String generateAppSourceRouteId(String appSourcePath) {
    	return String.format("%s_%s", APP_GATEWAY_ENDPOINT_ID, appSourcePath);
    }
}

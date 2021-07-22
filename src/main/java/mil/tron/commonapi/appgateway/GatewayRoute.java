package mil.tron.commonapi.appgateway;

import org.apache.camel.CamelContext;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.support.builder.PredicateBuilder;

public class GatewayRoute extends RouteBuilder {
	private String appSourcePath;
	
	public GatewayRoute(CamelContext camelContext, String appSourcePath) {
		super(camelContext);
		this.appSourcePath = appSourcePath;
	}
	
	@Override
    public void configure() throws Exception {
		Predicate throttleEnabled = header("is-throttle-enabled").isEqualTo(true);
		Predicate throttleRateLimit = header("throttle-rate-limit").isGreaterThanOrEqualTo(0);
		
        from(AppGatewayRouteBuilder.generateAppSourceRouteUri(appSourcePath))
	        .id(AppGatewayRouteBuilder.generateAppSourceRouteId(appSourcePath))
	        .streamCaching()
	        .choice()
	        	.when(PredicateBuilder.and(throttleEnabled, throttleRateLimit))
	        		.throttle(ExpressionBuilder.headerExpression("throttle-rate-limit")).timePeriodMillis(60000).rejectExecution(true)
	        		.toD("${header.request-url}")
	        .endChoice()
	        	.otherwise()
	        		.toD("${header.request-url}")
	        .end();
    }
}
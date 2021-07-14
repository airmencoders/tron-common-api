package mil.tron.commonapi.service;

import mil.tron.commonapi.appgateway.AppGatewayRouteBuilder;
import mil.tron.commonapi.appgateway.AppSourceConfig;
import mil.tron.commonapi.appgateway.AppSourceInterfaceDefinition;
import mil.tron.commonapi.dto.appsource.AppSourceDetailsDto;
import mil.tron.commonapi.entity.appsource.AppSource;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.processor.ThrottlerRejectedExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AppGatewayServiceImpl implements AppGatewayService {

	private AppSourceService appSourceService;
	private AppSourceConfig appSourceConfig;
	
    FluentProducerTemplate producer;

    @Autowired
    AppGatewayServiceImpl(FluentProducerTemplate producer, AppSourceService appSourceService, AppSourceConfig appSourceConfig) {
        this.producer = producer;
        this.appSourceService = appSourceService;
        this.appSourceConfig = appSourceConfig;
    }

    /**
     * Forwards the request to an App Source based on the app source path.
     * @param request The Servlet Request provided for forwarding
     * @return The result byte[] from the App Source
     * @throws ResponseStatusException Caused by a non 2xx response from the App Source.
     * @throws IOException Error converting to byte[] from result input stream.
     */
    public byte[] sendRequestToAppSource(HttpServletRequest request)
            throws ResponseStatusException, IOException {
    	Map<String, AppSourceInterfaceDefinition> appSourcePathToDefMap = this.appSourceConfig.getPathToDefinitionMap();
    	Map<AppSourceInterfaceDefinition, AppSource>  appSourceDefToEntityMap = this.appSourceConfig.getAppSourceDefs();
    	
        String sendToPath = this.buildPathForAppSource(request.getRequestURI());
        String appPath = this.buildAppPath(request.getRequestURI());
        // use producer to send
        AppSourceInterfaceDefinition appSourceDef = appSourcePathToDefMap.get(appPath);
        if (appSourceDef == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    String.format("No App Source for %s.", appPath));
        }
        String endpointString = appSourceDef.getSourceUrl() + sendToPath + "?" + request.getQueryString() +
                "&bridgeEndpoint=true";
        byte[] response = null;
        
        String body = "";
        try {
        	body = request.getReader().lines().collect(Collectors.joining());
        } catch (Exception ex) {
        	throw new ResponseStatusException(HttpStatus.valueOf(400), ex.getMessage());
        }
        
        AppSourceDetailsDto appSourceDetails = appSourceService.getAppSource(appSourceDefToEntityMap.get(appSourceDef).getId());

        try {
            InputStream streamResponse = (InputStream) producer.to(AppGatewayRouteBuilder.generateAppSourceRouteUri(appSourceDef.getAppSourcePath()))
				.withHeader("request-url", endpointString)
				.withHeader("is-throttle-enabled", appSourceDetails.isThrottleEnabled())
				.withHeader("throttle-rate-limit", appSourceDetails.getThrottleRequestCount())
				.withHeader(Exchange.HTTP_METHOD, request.getMethod())
				.withHeader(Exchange.CONTENT_TYPE, request.getContentType())
				.withBody(body)
	            .request();
            
            response = streamResponse.readAllBytes();
            streamResponse.close();
        }
        catch (CamelExecutionException e) {
        	/**
        	 * Handles error responses received from the App Source.
        	 */
        	if (e.getCause() instanceof HttpOperationFailedException) {
        		HttpOperationFailedException exception = (HttpOperationFailedException) e.getCause();

                throw new ResponseStatusException(
                        HttpStatus.valueOf(exception.getStatusCode()),
                        exception.getResponseBody());
        	}
        	
        	/**
        	 * Handles errors due to requests exceeding the maximum throttle rate
        	 * set for the App Source.
        	 */
        	if (e.getCause() instanceof ThrottlerRejectedExecutionException) {
        		ThrottlerRejectedExecutionException exception = (ThrottlerRejectedExecutionException) e.getCause();

                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        exception.getLocalizedMessage());
        	}
            
        	/**
        	 * Handles all other exceptions. This may occur under conditions in which the
        	 * request could not be sent out. For example, if the App Source Url is bad
        	 * or the App Source is down and cannot respond to the request.
        	 */
        	throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Error communicating with " + appSourceDef.getName());
        }
        return response;
    }


    /***
     * Expected uri string /api/v1/app/{appsource}/{appsource-request-path}
     * @param uriRequest
     * @return String for the path on the app source api to send the request.
     */
    public String buildPathForAppSource(String uriRequest) {
        if (uriRequest == null) {
            return null;
        }
        String[] requestUri = uriRequest.split("/");
        String[] appSourcePathParts = Arrays.asList(requestUri).subList(5, requestUri.length)
                .toArray(new String[0]);

        return "/" + String.join("/", appSourcePathParts);
    }

    /***
     * Expected uri string /api/v1/app/{appsource}/{appsource-request-path}
     * @param uriRequest
     * @return String for the app path.
     */
    public String buildAppPath(String uriRequest) {
        if (uriRequest == null) {
            return null;
        }
        String[] requestUri = uriRequest.split("/");
        if (requestUri.length < 5) {
            return "";
        }
        return requestUri[4];
    }
}

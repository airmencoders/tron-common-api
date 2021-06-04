package mil.tron.commonapi.service;

import mil.tron.commonapi.appgateway.AppGatewayRouteBuilder;
import mil.tron.commonapi.appgateway.AppSourceInterfaceDefinition;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AppGatewayServiceImpl implements AppGatewayService {

    FluentProducerTemplate producer;

    Map<String, AppSourceInterfaceDefinition> appSourceDefMap = new HashMap<>();

    @Autowired
    AppGatewayServiceImpl(FluentProducerTemplate producer) {
        this.producer = producer;
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
        String sendToPath = this.buildPathForAppSource(request.getRequestURI());
        String appPath = this.buildAppPath(request.getRequestURI());
        // use producer to send
        AppSourceInterfaceDefinition appSourceDef = this.appSourceDefMap.get(appPath);
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

        try {
            InputStream streamResponse = (InputStream) producer.to(AppGatewayRouteBuilder.APP_GATEWAY_ENDPOINT)
				.withHeader("request-url", endpointString)
				.withHeader(Exchange.HTTP_METHOD, request.getMethod())
				.withHeader(Exchange.CONTENT_TYPE, request.getContentType())
				.withBody(body)
	            .request();
            
            response = streamResponse.readAllBytes();
            streamResponse.close();
        }
        catch (CamelExecutionException e) {
        	if (e.getCause() instanceof HttpOperationFailedException) {
        		HttpOperationFailedException exception = (HttpOperationFailedException) e.getCause();

                throw new ResponseStatusException(
                        HttpStatus.valueOf(exception.getStatusCode()),
                        exception.getResponseBody());
        	}
            
        	throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Error communicating with " + appSourceDef.getName());
        }
        return response;
    }

    /**
     * Adds a source def mapping to the map
     * @param appSourcePath
     * @param appDef
     * @return True if the app def is added. False if the app source path is not added and was already defined.
     */
    public boolean addSourceDefMapping(String appSourcePath, AppSourceInterfaceDefinition appDef) {
        if (this.appSourceDefMap.get(appSourcePath) == null) {
            this.appSourceDefMap.put(appSourcePath, appDef);
            return true;
        }
        return false;
    }

    @Override
    public void clearAppSourceDefs() {
        this.appSourceDefMap.clear();
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

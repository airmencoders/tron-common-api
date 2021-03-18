package mil.tron.commonapi.service;

import mil.tron.commonapi.appgateway.AppSourceInterfaceDefinition;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.Produce;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.http.client.HttpResponseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Service
public class AppGatewayServiceImpl implements AppGatewayService {

    @Produce
    FluentProducerTemplate producer;

    Map<String, AppSourceInterfaceDefinition> appSourceDefMap = new HashMap<>();

    public String sendRequestToAppSource(HttpServletRequest request) throws HttpResponseException {
        String sendToPath = this.buildPathForAppSource(request.getRequestURI());
        String appPath = this.buildAppPath(request.getRequestURI());
        // use producer to send
        AppSourceInterfaceDefinition appSourceDef = this.appSourceDefMap.get(appPath);
        String endpointString = appSourceDef.getSourceUrl() + sendToPath + "?" + request.getQueryString() +
                "&bridgeEndpoint=true";
        String response = null;
        try {
            response = producer.to(endpointString).request(String.class);
        } catch (Exception e) {
            HttpOperationFailedException exception = (HttpOperationFailedException) e.getCause();
            throw new HttpResponseException(exception.getStatusCode(),
                    exception.getResponseBody());

        }
        return response;
    }

    public void addSourceDefMapping(String appSourceName, AppSourceInterfaceDefinition appDef) {
        this.appSourceDefMap.put(appSourceName, appDef);
    }

    /***
     * Expected uri string /api/v1/app/{appsource}/{appsource-request-path}
     * @param uriRequest
     * @return String for the path on the app source api to send the request.
     */
    public String buildPathForAppSource(String uriRequest) {
        String[] requestUri = uriRequest.split("/");
        String[] appSourcePathParts = Arrays.asList(requestUri).subList(5, requestUri.length)
                .toArray(new String[0]);
        String sendToPath = "/" + String.join("/", appSourcePathParts);

        return sendToPath;
    }

    /***
     * Expected uri string /api/v1/app/{appsource}/{appsource-request-path}
     * @param uriRequest
     * @return String for the app path.
     */
    public String buildAppPath(String uriRequest) {
        String[] requestUri = uriRequest.split("/");
        if (requestUri.length < 5) {
            return "";
        }
        return requestUri[4];
    }
}

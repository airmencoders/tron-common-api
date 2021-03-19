package mil.tron.commonapi.service;

import mil.tron.commonapi.appgateway.AppSourceInterfaceDefinition;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.http.client.HttpResponseException;
import org.springframework.core.io.InputStreamResource;

import javax.servlet.http.HttpServletRequest;

public interface AppGatewayService {
    InputStreamResource sendRequestToAppSource(HttpServletRequest request) throws HttpOperationFailedException;
    void addSourceDefMapping(String appSourceName, AppSourceInterfaceDefinition appDef);

}

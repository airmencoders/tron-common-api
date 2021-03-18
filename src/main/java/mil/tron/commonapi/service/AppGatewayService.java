package mil.tron.commonapi.service;

import mil.tron.commonapi.appgateway.AppSourceInterfaceDefinition;
import org.apache.http.client.HttpResponseException;

import javax.servlet.http.HttpServletRequest;

public interface AppGatewayService {
    String sendRequestToAppSource(HttpServletRequest request) throws HttpResponseException;
    void addSourceDefMapping(String appSourceName, AppSourceInterfaceDefinition appDef);

}

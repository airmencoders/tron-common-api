package mil.tron.commonapi.service;

import mil.tron.commonapi.appgateway.AppSourceInterfaceDefinition;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;

public interface AppGatewayService {
    byte[] sendRequestToAppSource(HttpServletRequest request) throws ResponseStatusException,
            IOException;
    boolean addSourceDefMapping(String appSourcePath, AppSourceInterfaceDefinition appDef);
    Map<String, AppSourceInterfaceDefinition> getDefMap();
    void clearAppSourceDefs();
}

package mil.tron.commonapi.service;

import mil.tron.commonapi.appgateway.AppSourceInterfaceDefinition;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

public interface AppGatewayService {
    byte[] sendRequestToAppSource(HttpServletRequest request) throws ResponseStatusException,
            IOException;
    boolean addSourceDefMapping(String appSourcePath, AppSourceInterfaceDefinition appDef);

    void clearAppSourceDefs();
}

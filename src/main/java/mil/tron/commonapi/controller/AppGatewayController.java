package mil.tron.commonapi.controller;

import mil.tron.commonapi.appgateway.AppSourceInterfaceDefinition;
import mil.tron.commonapi.service.AppGatewayService;
import org.apache.camel.*;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Controller
public class AppGatewayController {

    private AppGatewayService appGatewayService;

    @Autowired
    AppGatewayController(AppGatewayService appGatewayService) {
        this.appGatewayService = appGatewayService;
    }

    // add annotation for authorization wrt client app request
    public ResponseEntity<String> handleGetRequests(HttpServletRequest request,
                                                 @PathVariable Map<String, String> vars) throws Exception {
        String response = "";
        try {
            response = this.appGatewayService.sendRequestToAppSource(request);
        } catch (Exception e) {
            HttpOperationFailedException exception = (HttpOperationFailedException) e.getCause();
            return new ResponseEntity<String>(
                    exception.getResponseBody(),
                    HttpStatus.valueOf(exception.getStatusCode()));
        }
        return new ResponseEntity<>(response, HttpStatus.OK);
    }




}

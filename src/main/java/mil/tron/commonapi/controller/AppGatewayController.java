package mil.tron.commonapi.controller;

import liquibase.pro.packaged.J;
import mil.tron.commonapi.service.AppGatewayService;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.http.client.HttpResponseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Controller
public class AppGatewayController {

    private AppGatewayService appGatewayService;

    @Autowired
    AppGatewayController(AppGatewayService appGatewayService) {
        this.appGatewayService = appGatewayService;
    }

    // add annotation for authorization wrt client app request
    public ResponseEntity<InputStreamResource> handleGetRequests(HttpServletRequest request,
                                                        HttpServletResponse responseObject,
                                                        @PathVariable Map<String, String> vars) throws Exception {
        InputStreamResource response = null;
        HttpHeaders headers = new HttpHeaders();
        response = this.appGatewayService.sendRequestToAppSource(request);
        headers.setContentLength(request.getContentLength());

        return new ResponseEntity<>(response, headers, HttpStatus.OK);
    }




}

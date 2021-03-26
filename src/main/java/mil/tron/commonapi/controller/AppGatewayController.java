package mil.tron.commonapi.controller;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;

import mil.tron.commonapi.annotation.security.PreAuthorizeGatewayRead;
import mil.tron.commonapi.service.AppGatewayService;

@Controller
public class AppGatewayController {

    private AppGatewayService appGatewayService;

    @Autowired
    AppGatewayController(AppGatewayService appGatewayService) {
        this.appGatewayService = appGatewayService;
    }

    @PreAuthorizeGatewayRead()
    public ResponseEntity<byte[]> handleGetRequests(HttpServletRequest requestObject,
                                                    HttpServletResponse responseObject, @PathVariable Map<String, String> vars) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        byte[] response = this.appGatewayService.sendRequestToAppSource(requestObject);
        if (response != null ) {
            headers.setContentLength(response.length);
            return new ResponseEntity<>(response, headers, HttpStatus.OK);
        }
        byte[] emptyArray = new byte[0];
        return new ResponseEntity<>(emptyArray, headers, HttpStatus.NO_CONTENT);
    }
}

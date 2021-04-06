package mil.tron.commonapi.controller;

import mil.tron.commonapi.exception.InvalidAppSourcePermissions;
import mil.tron.commonapi.service.AppGatewayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class AppGatewayController {

    private AppGatewayService appGatewayService;

    @Value("${api-prefix.v1}")
    private String apiVersion;

    @Autowired
    AppGatewayController(AppGatewayService appGatewayService) {
        this.appGatewayService = appGatewayService;
    }

    public ResponseEntity<byte[]> handleGetRequests(HttpServletRequest requestObject,
                                                    HttpServletResponse responseObject,
                                                    @PathVariable Map<String, String> vars)
            throws ResponseStatusException,
                    IOException,
                    InvalidAppSourcePermissions {

        // get the list of privs and endpoints for this requester from spring security
        List<String> authPaths = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getAuthorities()
                .stream().map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        // get the spring-matched request mapping -- trim off the beginning prefix (e.g. /v1/app/)
        String patternMatched = requestObject
                .getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)
                .toString()
                .replaceFirst("^" + apiVersion + "/app/", "");

        // check if the requestor has this request mapping in their privs, if not reject the request
        if (!authPaths.contains(patternMatched)) {
            throw new InvalidAppSourcePermissions("Requester does not have access to this endpoint");
        }

        // if we get here, then request is passed off to camel for routing to the app-source proper
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

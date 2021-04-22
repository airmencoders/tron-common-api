package mil.tron.commonapi.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache.ValueWrapper;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerMapping;

import mil.tron.commonapi.exception.InvalidAppSourcePermissions;
import mil.tron.commonapi.service.AppGatewayService;

@Controller
public class AppGatewayController {

    private AppGatewayService appGatewayService;

    @Value("${api-prefix.v1}")
    private String apiVersion;

    @Autowired(required = false)
    private CacheManager cacheManager;

    @Autowired
    AppGatewayController(AppGatewayService appGatewayService) {
        this.appGatewayService = appGatewayService;
    }

    // @Cacheable(keyGenerator = "GatewayKeyGenerator", cacheNames = "gateway-cache")
    public ResponseEntity<byte[]> handleGetRequests(HttpServletRequest requestObject, HttpServletResponse responseObject, @PathVariable Map<String, String> vars) //NOSONAR
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
        HttpHeaders headers = new HttpHeaders();

        int appSourceAndEndpointSeparator = patternMatched.indexOf("/");
        
        // cacheManager Bean may not exist; if caching.enabled = false
        if(appSourceAndEndpointSeparator > -1 && cacheManager != null) {
            return cacheCheck(requestObject, patternMatched, headers, appSourceAndEndpointSeparator);
        }
        // if we get here, then request is passed off to camel for routing to the app-source proper
        return performRequest(requestObject, headers);
    }

    private ResponseEntity<byte[]> cacheCheck(HttpServletRequest requestObject, String trimmedURI,
            HttpHeaders headers, int appSourceAndEndpointSeparator) throws IOException {
        String appSourcePath = trimmedURI.substring(0, appSourceAndEndpointSeparator);
        String cacheKey = trimmedURI.substring(appSourceAndEndpointSeparator, trimmedURI.length()) + "_" +  requestObject.getQueryString();
        System.out.println();
        System.out.println("~".repeat(150));
        System.out.println("key: " + cacheKey);
        System.out.println("appSourcePath: " + appSourcePath);
        System.out.println();

        ValueWrapper cached = cacheManager.getCache(appSourcePath).get(cacheKey);
        if(cached != null && cached.get() instanceof byte[]) {
            return new ResponseEntity<>((byte[]) cached.get(), headers, HttpStatus.OK);
        } else {
            System.out.println("-".repeat(100));
            System.out.println("Putting into cache");
            System.out.println("-".repeat(100));
            ResponseEntity<byte[]> responseEntity = performRequest(requestObject, headers);
            cacheManager.getCache(appSourcePath).put(cacheKey, responseEntity.getBody());
            return responseEntity;
        }
    }

    private ResponseEntity<byte[]> performRequest(HttpServletRequest requestObject, HttpHeaders headers) throws IOException {
        byte[] response = this.appGatewayService.sendRequestToAppSource(requestObject);
        if (response != null ) {
            headers.setContentLength(response.length);
            return new ResponseEntity<>(response, headers, HttpStatus.OK);
        }
        byte[] emptyArray = new byte[0];
        return new ResponseEntity<>(emptyArray, headers, HttpStatus.NO_CONTENT);
    }
}

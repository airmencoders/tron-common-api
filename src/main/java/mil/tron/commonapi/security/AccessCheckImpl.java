package mil.tron.commonapi.security;

import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import mil.tron.commonapi.service.utility.ResolvePathFromRequest;

public class AccessCheckImpl implements AccessCheck {
    
    private String apiVersionPrefix;

    public AccessCheckImpl(String apiVersionPrefix) {
        this.apiVersionPrefix = apiVersionPrefix;
    }

    @Override
    public boolean check(HttpServletRequest requestObject) {
        // get the list of privs and endpoints for this requester from spring security
        List<String> authPaths = SecurityContextHolder
            .getContext()
            .getAuthentication()
            .getAuthorities()
            .stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        // get the spring-matched request mapping -- trim off the beginning prefix (e.g. /v1/app/)
        String patternMatched = ResolvePathFromRequest.resolve(requestObject, apiVersionPrefix);

        // check if the requestor has this request mapping in their privs, if not reject the request
        return authPaths.contains(patternMatched + "_" + requestObject.getMethod().toString());
    }
    
}

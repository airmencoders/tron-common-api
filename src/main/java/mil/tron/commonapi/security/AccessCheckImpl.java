package mil.tron.commonapi.security;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.servlet.http.HttpServletRequest;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.util.UriTemplate;

import mil.tron.commonapi.ApplicationProperties;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.service.AppClientUserService;
import mil.tron.commonapi.service.utility.ResolvePathFromRequest;

public class AccessCheckImpl implements AccessCheck {
    
    private ApplicationProperties prefixProperties;
    private AppClientUserService appClientUserService;

    public AccessCheckImpl(ApplicationProperties prefixProperties, AppClientUserService appClientUserService) {
        this.prefixProperties = prefixProperties;        
        this.appClientUserService = appClientUserService;
    }

    @Override
    public boolean check(HttpServletRequest requestObject) {
    	/**
    	 * Dashboard User's do not get App Source Endpoint privileges. These privileges
    	 * only belong to App Clients.
    	 * 
    	 * To allow App Client developers to execute requests on their own, instead of going
    	 * through their respective App Client, we need to find all the App Clients that the
    	 * requesting Dashboard User is a developer for and consolidate all of the App Source
    	 * Endpoint privileges to find out what the developer has access to.
    	 * 
    	 * Check the authenticated user to see if they have corresponding endpoint
    	 * privilege first to avoid querying the database further. If that fails, then check for 
    	 * all App Client's the user is a developer for and aggregate all of the App Client privileges.
    	 */
    	
        // get the list of privs and endpoints for this requester from spring security
    	Authentication auth = SecurityContextHolder
                .getContext()
                .getAuthentication();
    	
        List<String> authPaths = auth
            .getAuthorities()
            .stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        // get the spring-matched request mapping -- trim off the beginning prefix (e.g. /v1/app/)
        String patternMatched = ResolvePathFromRequest.resolve(requestObject, this.prefixProperties.getCombinedPrefixes());
        String privilegeName = patternMatched + "_" + requestObject.getMethod();

        // check if the App Client requestor has this request mapping in their privs
        if (containsEndpointPrivilege(privilegeName, authPaths)) {
        	return true;
        }
        
        // If this isn't an App Client Developer just return
        // to avoid making unnecessary database calls
        if (!authPaths.contains("APP_CLIENT_DEVELOPER")) {
        	return false;
        } 
        
        Iterable<AppClientUser> appClients = appClientUserService.getAppClientUsersContainingDeveloperEmail(auth.getName());

        Set<String> aggregatedAppClientPrivileges = new HashSet<>();
        
        /**
         * For each App Client the Dashboard User is a developer for, consolidate its endpoint privilege
         * into an aggregated Set of endpoint privileges.
         */
        StreamSupport
			.stream(appClients.spliterator(), false)
			.map(AppClientUser::getAppEndpointPrivs)
			.forEach(appEndpointPrivileges -> {
				var paths = appEndpointPrivileges
						.stream()
						.map(priv -> String.format("%s%s_%s", priv.getAppEndpoint().getAppSource().getAppSourcePath(), priv.getAppEndpoint().getPath(), priv.getAppEndpoint().getMethod()))
						.collect(Collectors.toSet());
				aggregatedAppClientPrivileges.addAll(paths);
			});

        return containsEndpointPrivilege(privilegeName, aggregatedAppClientPrivileges);
    }
    
    /**
     * Helps to match against uris that contain path parameters.
     * For example "puckboard/jobRole/1" would match
     * to "puckboard/jobRole/{jobRoleId}"
     * 
     * @param privilegeUri the uri to match against
     * @param urisToCheck the uris to check
     * @return true if any uris in {@code urisToCheck} matches {@code privilegeUri}
     */
    private boolean containsEndpointPrivilege(String privilegeUri, Collection<String> urisToCheck) {
    	for (String endpointPrivilege : urisToCheck) {
        	UriTemplate uriTemplate = new UriTemplate(endpointPrivilege);
            if(uriTemplate.matches(privilegeUri)){
               return true;
            }
        }
    	
    	return false;
    }
}

package mil.tron.commonapi.security;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.repository.appsource.AppSourceRepository;

public class AccessCheckAppSourceImpl implements AccessCheckAppSource {

    private AppSourceRepository appSourceRepo;

    public AccessCheckAppSourceImpl(AppSourceRepository appSourceRepo) {
        this.appSourceRepo = appSourceRepo;
    }

    @Override
    public boolean checkByAppSourceId(Authentication authentication, String appSourceId) {
        if (authentication == null || appSourceId == null) {
            return false;
        }        
        
        UUID id = getUUID(appSourceId);
        if(id == null) {
            return false;
        }

        // get the list of privs and endpoints for this requester from spring security
        List<String> authPaths = unpackAuthentication(authentication);

        AppSource appSource = appSourceRepo.findById(id).orElse(null);
        return determinePermission(authPaths, appSource);
    }

    @Override
    public boolean checkByAppEndpointPrivId(Authentication authentication, String appEndpointPrivId) {
        if (authentication == null || appEndpointPrivId == null) {
            return false;
        }

        UUID id = getUUID(appEndpointPrivId);
        if(id == null) {
            return false;
        }

        // get the list of privs and endpoints for this requester from spring security
        List<String> authPaths = unpackAuthentication(authentication);

        AppSource appSource = appSourceRepo.findByAppPrivs_Id(id).orElse(null);
        return determinePermission(authPaths, appSource);
    }

    private boolean determinePermission(List<String> authPaths, AppSource appSource) {
        if(appSource == null) {
            return false;
        }
        // get the spring-matched request mapping -- trim off the beginning prefix (e.g. /v1/app/)
        final String appSourcePath = appSource.getAppSourcePath() + "/";
        // check if the requestor has this request mapping in their privs, if not reject the request
        return authPaths.stream().anyMatch(path -> path.startsWith(appSourcePath));
    }

    private UUID getUUID(String inputId) {        
        UUID id;
        try {
            id = UUID.fromString(inputId);
        } catch(IllegalArgumentException ex) {
            return null;
        }
        return id;
    }

    private List<String> unpackAuthentication(Authentication authentication) {
        return authentication
            .getAuthorities()
            .stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
    }
}

package mil.tron.commonapi.security;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.appsource.AppEndpointPrivRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.repository.appsource.AppSourceRepository;

public class AccessCheckAppSourceImpl implements AccessCheckAppSource {

    private AppSourceRepository appSourceRepo;
    private AppClientUserRespository appClientUserRespository;
    private AppEndpointPrivRepository appEndpointPrivRepository;

    public AccessCheckAppSourceImpl(AppSourceRepository appSourceRepo,
                                    AppClientUserRespository appClientUserRespository,
                                    AppEndpointPrivRepository appEndpointPrivRepository) {
        this.appSourceRepo = appSourceRepo;
        this.appClientUserRespository = appClientUserRespository;
        this.appEndpointPrivRepository = appEndpointPrivRepository;
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
        boolean isAllowedByAppClientAuth = determinePermission(authPaths, appSource);
        if (isAllowedByAppClientAuth) {
            return true;
        }
        if (authentication.getCredentials() == null) {
            return false;
        }
        List<AppClientUser> appClientsForDev = this.appClientUserRespository.findByAppClientDevelopersEmailIgnoreCase(
                authentication.getCredentials().toString());
        boolean isAuthorizedClient = appClientsForDev.stream().anyMatch(
                appClient -> this.appEndpointPrivRepository.existsByAppSourceEqualsAndAppClientUserEquals(
                        appSource, appClient)
        );
        return isAuthorizedClient;
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

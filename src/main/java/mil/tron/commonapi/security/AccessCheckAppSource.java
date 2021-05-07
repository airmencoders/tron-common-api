package mil.tron.commonapi.security;

import org.springframework.security.core.Authentication;

public interface AccessCheckAppSource {
    
    /**
     * Checks the app source path of the app source (found by id) against the user held endpoint granted authorities. 
     * User held granted authoities are expected to be in the format [path_method], e.g. app-source/end/point/path_GET
     * Since endpoint granted authorities include the app source path, any held end point authority will also contain the app source path followed by a "/"
     * @param authentication the authentication object
     * @param appSourceId the Id to check 
     * @return true if any user held authority contains the app source path followed by a "/", e.g. my-app-source/ in my-app-source/end/point/path_GET
     */
    boolean checkByAppSourceId(Authentication authentication, String appSourceId);

        /**
     * Checks the app source path of the app source (found joining to get App Source) against the user held endpoint granted authorities. 
     * User held granted authoities are expected to be in the format [path_method], e.g. app-source/end/point/path_GET
     * Since endpoint granted authorities include the app source path, any held end point authority will also contain the app source path followed by a "/"
     * @param authentication the authentication object
     * @param appEndpointPrivId the Id to use to find the App Source to check authorities with
     * @return true if any user held authority contains the related app source path followed by a "/", e.g. my-app-source/ in my-app-source/end/point/path_GET
     */
    boolean checkByAppEndpointPrivId(Authentication authentication, String appEndpointPrivId);
}

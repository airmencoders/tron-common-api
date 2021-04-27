package mil.tron.commonapi.security;

import javax.servlet.http.HttpServletRequest;

public interface AccessCheck {
    /**
     * Checks the path + method of the request against the user held endpoint granted authorities.
     * User held granted authoities are expected to be in the format [path_method], e.g. app-source/end/point/path_GET
     * @param request the HttpServletRequest to find the path with. Must not be null.
     * @return true if any authority matches path + request method, false otherwise
     */
    boolean check(HttpServletRequest request);
}

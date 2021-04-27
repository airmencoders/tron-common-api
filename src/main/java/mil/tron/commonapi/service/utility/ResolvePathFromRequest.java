package mil.tron.commonapi.service.utility;

import javax.servlet.http.HttpServletRequest;

public class ResolvePathFromRequest {
    private ResolvePathFromRequest() {}

    /**
     * Get the spring-matched request mapping -- trim off the beginning prefix (e.g. /v1/app/)
     * @param request the request to parse
     * @param apiVersionPrefix the api version prefix to remove from the path (e.g. /v1)
     * @return String path. Returns empty string if request is null
     */
    public static String resolve(HttpServletRequest request, String apiVersionPrefix) {
        if(request == null) {
            return "";
        }
        if(apiVersionPrefix == null) {
            apiVersionPrefix = "";
        }
        return request
                .getRequestURI()
                .replaceFirst("^" + apiVersionPrefix + "/app/", "");
    }
}

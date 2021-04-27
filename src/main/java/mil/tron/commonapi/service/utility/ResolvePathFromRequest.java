package mil.tron.commonapi.service.utility;

import javax.servlet.http.HttpServletRequest;

public class ResolvePathFromRequest {
    private ResolvePathFromRequest() {}

    /**
     * Get the spring-matched request mapping -- trim off the beginning prefix (e.g. /v1/app/)
     * @param request the request to parse
     * @param contextPath the prefix used as the context path
     * @param apiVersionPrefix the api version prefix to remove from the path (e.g. /v1)
     * @param appSourcesPrefix the prefix used for all app sources
     * @return String path, without the given prefix. Returns empty string if request is null.
     */
    public static String resolve(HttpServletRequest request, String prefix) {
        if(request == null) {
            return "";
        }
        if(prefix == null) {
            return request.getRequestURI();
        }
        return request
                .getRequestURI()
                .replaceFirst("^(.*" + prefix + "/)", "");
    }
}

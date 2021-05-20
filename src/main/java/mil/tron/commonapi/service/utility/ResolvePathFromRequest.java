package mil.tron.commonapi.service.utility;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

public class ResolvePathFromRequest {
    private ResolvePathFromRequest() {}

    /**
     * Get the spring-matched request mapping -- trim off the beginning prefix (e.g. /v1/app/)
     * @param request the request to parse
     * @param prefixes the list of prefixes to test against the path. Only one will be used!
     * @return String path, without the first found prefix contained in the request. Returns empty string if request is null.
     */
    public static String resolve(HttpServletRequest request, List<String> prefixes) {
        if(request == null) {
            return "";
        }
        String uri = request.getRequestURI();
        if(prefixes == null || prefixes.isEmpty()) {
            return uri;
        }
        String prefixToReplaceWith = prefixes.stream().filter(prefix -> uri.contains(prefix)).findFirst().orElse("");
        if(prefixToReplaceWith.isEmpty()) {
            return uri;
        }
        return uri.replaceFirst("^(.*" + prefixToReplaceWith + "/)", "");
    }

}

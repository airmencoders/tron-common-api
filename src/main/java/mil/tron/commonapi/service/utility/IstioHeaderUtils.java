package mil.tron.commonapi.service.utility;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods for reading/parsing the Istio injected XFCC (X-Forwarded-Client-Cert) header
 */

public class IstioHeaderUtils {

    private IstioHeaderUtils() {}
    private static final String NAMESPACE_REGEX =
            "(?:http://[^\\\\.]+\\.([^\\\\.]+)(?=\\.svc\\.cluster\\.local))" +  // match format for cluster-local URI
                    "|http://localhost:([0-9]+)";  // alternatively match localhost for dev/test
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile(NAMESPACE_REGEX);

    /**
     * Extracts the namespace from a cluster-local URI that is a subscriber's registered webhook URL
     * This is not to be confused with the URI in the XFCC header that Istio injects, that is something different.
     * This one has a format like http://app-name.ns.svc.cluster.local/ and represents a dns name local to the cluster
     *
     * Alternatively for dev and test, it will be in format of http://localhost:(\d+), where the port number will
     * be the so-called 'namespace'
     * @param uri Registered URL of the subscriber getting a broadcast
     * @return Namespace of the subscriber's webhook URL, or "" blank string if no namespace found
     */
    public static String extractSubscriberNamespace(String uri) {
        if (uri == null) return "";

        // namespace in a cluster local address should be 2nd element in the URI
        Matcher extractNs = NAMESPACE_PATTERN.matcher(uri);
        boolean found = extractNs.find();
        if (found && extractNs.group(1) != null) return extractNs.group(1);  // matched P1 cluster-local format
        else if (found && extractNs.group(2) != null) return extractNs.group(2);  // matched localhost format
        else return "";  // no matches found
    }
}

package mil.tron.commonapi;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

/**
 * Some commonly used methods for emulating Istio auth headers
 */
public class JwtUtils {
    
    private JwtUtils() {}

    public static final String XFCC_HEADER_NAME = "x-forwarded-client-cert";
    public static final String AUTH_HEADER_NAME = "authorization";

    /**
     * Private helper to create a JWT on the fly
     * @param email email to embed with the "email" claim
     * @return the bearer token
     */
    public static String createToken(String email) {
        Algorithm algorithm = Algorithm.HMAC256("secret");
        return "Bearer " + JWT.create()
                .withIssuer("istio")
                .withClaim("email", email)
                .sign(algorithm);
    }

    /**
     * Generate and return a K8s formatted app client URI
     * @param namespace name of the app client to emulate
     * @return
     */
    public static String generateXfccHeader(String namespace) {
        String XFCC_BY = "By=spiffe://cluster/ns/" + namespace + "/sa/default";
        String XFCC_H = "FAKE_H=12345";
        String XFCC_SUBJECT = "Subject=\\\"\\\";";
        return new StringBuilder()
                .append(XFCC_BY)
                .append(XFCC_H)
                .append(XFCC_SUBJECT)
                .append("URI=spiffe://cluster.local/ns/" + namespace + "/sa/default")
                .toString();
    }

    /**
     * Helper to generate a XFCC header from the istio gateway
     * @return
     */
    public static String generateXfccHeaderFromSSO() {
        return generateXfccHeader("istio-system");
    }

}

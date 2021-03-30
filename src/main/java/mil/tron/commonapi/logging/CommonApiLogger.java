package mil.tron.commonapi.logging;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;


@Aspect
@Component
@Configuration
@Slf4j
public class CommonApiLogger {

    private HttpServletRequest request;

    public CommonApiLogger(HttpServletRequest request) { this.request = request; }
    // decodes the Istio JWT
    private DecodedJWT decodeJwt (HttpServletRequest request) {
        String bearer = request.getHeader("authorization");
        return JWT.decode(bearer.split("Bearer ")[1]);
    }

    // grab the email from the Istio JWT for increased logging granularity
    private String getRequestorEmail(){
        if (request == null || request.getHeaderNames() == null) return "Unknown";
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()){
            if (headerNames.nextElement().equals("authorization")){
                DecodedJWT decodedJwt = decodeJwt(request);

                // mask request's email as its MD5 string
                return DigestUtils.md5DigestAsHex(decodedJwt.getClaim("email").asString().getBytes());
            }
        }
        return "Unknown";
    }

    // log the Advice from the JoinPoints below that match their defined Pointcuts
    public void logAdvice(String entry) {
        log.info("[Request from: " + getRequestorEmail() +  "] - " + entry);
    }

    // log when exceptions occur app-wide
    @AfterThrowing(pointcut = "(execution (* mil.tron.commonapi.*.*.*(..))) || (execution (* mil.tron.commonapi.*.*(..)))", throwing="ex")
    public void exceptionThrown(JoinPoint jp, Exception ex) {
        logAdvice("Exception thrown in " + jp.getSignature().getName() + " threw: " + ex);
    }

    // log all GET requests
    @Before("@annotation(org.springframework.web.bind.annotation.GetMapping)")
    public void beforeGetRequest(JoinPoint jp) {
        logAdvice("GET request for " + jp.getSignature().toLongString());
    }

    // log all POST requests
    @Before("@annotation(org.springframework.web.bind.annotation.PostMapping)")
    public void beforePostRequest(JoinPoint jp) {
        logAdvice("POST request for " + jp.getSignature().toLongString());
    }

    // log all PUT requests
    @Before("@annotation(org.springframework.web.bind.annotation.PutMapping)")
    public void beforePutRequest(JoinPoint jp) {
        logAdvice("PUT request for " + jp.getSignature().toLongString());
    }

    // log all DELETE requests
    @Before("@annotation(org.springframework.web.bind.annotation.DeleteMapping)")
    public void beforeDeleteRequest(JoinPoint jp) {
        logAdvice("DELETE request for " + jp.getSignature().toLongString());
    }
}

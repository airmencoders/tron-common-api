package mil.tron.commonapi.logging;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;


@Aspect
@Component
@Configuration
@Slf4j
public class CommonApiLogger {

    @Autowired HttpServletRequest req;

    // decodes the Istio JWT
    private DecodedJWT decodeJwt (HttpServletRequest request) {
        String bearer = request.getHeader("authorization");
        return JWT.decode(bearer.split("Bearer ")[1]);
    }

    // grab the email from the Istio JWT for increased logging granularity
    private String getRequestorEmail(){
        Enumeration<String> headerNames = req.getHeaderNames();
        while (headerNames.hasMoreElements()){
            if (headerNames.nextElement().equals("authorization")){
                DecodedJWT decodedJwt = decodeJwt(req);
                return decodedJwt.getClaim("email").asString();
            }
        }
        return "Unknown";
    }

    // log the Advice from the JoinPoints below that match their defined Pointcuts
    private void logAdvice(String entry) {
        // change level of logging here if needed
        log.info("[Request from: " + getRequestorEmail() +  "] - " + entry);
    }

    // log all method calls in the PersonController
    @Before("execution(* mil.tron.commonapi.controller.PersonController.*(..))")
    private void logEndpointAccess(JoinPoint jp) {
        logAdvice("Accessing '" + jp.getSignature().getName() + "'");
    }

    // log all GET requests
    @Before("@annotation(org.springframework.web.bind.annotation.GetMapping)")
    private void beforeGetRequest(JoinPoint jp) {
        logAdvice("GET request for " + jp.getSignature().toLongString());
    }

    // log all POST requests
    @Before("@annotation(org.springframework.web.bind.annotation.PostMapping)")
    private void beforePostRequest(JoinPoint jp) {
        logAdvice("POST request for " + jp.getSignature().toLongString());
    }
}
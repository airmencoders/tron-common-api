package mil.tron.commonapi.appgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.lang.reflect.Method;

import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.springframework.web.util.ContentCachingRequestWrapper;

public class GatewayKeyGeneratorTest {
    @Test
    void cacheOnControllerGeneratesCorrectKey() throws Exception {
        GatewayKeyGenerator gkg = new GatewayKeyGenerator();
        Object target = new Object();
        Method method = target.getClass().getMethod("equals", Object.class);
        HttpServletRequest request = get("/v1/app/mock/test?abc=123").buildRequest(null);

        Object key = gkg.generate(target, method, new ContentCachingRequestWrapper(request));

        if(key instanceof String) {
            assertEquals((String) key, "Object_equals_/v1/app/mock/test_GET_abc_123");
        } else {
            fail("Unexpected return type");
        }
    }

    @Test
    public void handlesKeyGenerationForNonContentCachingRequestWrapperInput() throws Exception {
        GatewayKeyGenerator gkg = new GatewayKeyGenerator();
        Object target = new Object();
        Method method = target.getClass().getMethod("equals", Object.class);

        Object key = gkg.generate(target, method, target);

        if(key instanceof String) {
            assertEquals((String) key, "Object_equals_" + target.toString());
        } else {
            fail("Unexpected return type");
        }
    }

    @Test
    public void handlesKeyGenerationForNoParamInput() throws Exception {
        GatewayKeyGenerator gkg = new GatewayKeyGenerator();
        Object target = new Object();
        Method method = target.getClass().getMethod("equals", Object.class);

        Object key = gkg.generate(target, method);

        if(key instanceof String) {
            assertEquals((String) key, "Object_equals_");
        } else {
            fail("Unexpected return type");
        }
    }
}

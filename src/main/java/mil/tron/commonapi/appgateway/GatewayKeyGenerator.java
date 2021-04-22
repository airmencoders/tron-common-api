package mil.tron.commonapi.appgateway;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.util.StringUtils;
import org.springframework.web.util.ContentCachingRequestWrapper;

public class GatewayKeyGenerator implements KeyGenerator {

    // Build the key to be in the format classname_methodname_/uri/path/_requestmethod_requestParam1_value1_requestParam2_value2
    // e.g. AppGatewayController_handleGetRequests_/api/v1/app/arms-gateway-local/aircrew-svc/flyer-7-30-60-90_GET_fromDate_2020-12-11:000000_toDate_2020-12-15:000000_inverse_1_harmCode_ABCD_systemId_1_recStatus_N
    @Override
    public Object generate(Object target, Method method, Object... params) {
        List<String> keyParts = new ArrayList<String>();
        keyParts.add(target.getClass().getSimpleName());
        keyParts.add(method.getName());
        
        if(params.length > 0 && params[0] instanceof ContentCachingRequestWrapper) {
            ContentCachingRequestWrapper ccrw = (ContentCachingRequestWrapper) params[0];
            keyParts.add(ccrw.getRequestURI());
            keyParts.add(ccrw.getMethod().toString());

            Map<String, String[]> paramsMap = ccrw.getParameterMap();
            for(String paramKey : paramsMap.keySet()) {
                keyParts.add(paramKey);
                keyParts.addAll(Arrays.asList(paramsMap.get(paramKey)));
            }
        } else {
            keyParts.add(StringUtils.arrayToDelimitedString(params, "_"));
        }

        return String.join("_", keyParts);
    } 
}

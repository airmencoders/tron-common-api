package mil.tron.commonapi.appgateway;

import java.lang.reflect.Method;
import java.util.Map;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.util.StringUtils;
import org.springframework.web.util.ContentCachingRequestWrapper;

public class GatewayKeyGenerator implements KeyGenerator {

    @Override
    public Object generate(Object target, Method method, Object... params) {
        String key =  target.getClass().getSimpleName() + "_"
            + method.getName() + "_";
        if(params.length > 0 && params[0] instanceof ContentCachingRequestWrapper) {
            ContentCachingRequestWrapper ccrw = (ContentCachingRequestWrapper) params[0];
            key += ccrw.getRequestURI();
            Map<String, String[]> paramsMap = ccrw.getParameterMap();
            for(String paramKey : paramsMap.keySet()) {
                key += "_" + paramKey + "_" + StringUtils.arrayToDelimitedString(paramsMap.get(paramKey), "_");
            }
            System.out.println();
        } else {
            key += StringUtils.arrayToDelimitedString(params, "_");
        }
        
        System.out.println();
        for(int i=0;i<100;i++){
            System.out.print("~");
        }
        System.out.println();
        System.out.println(key);
        return key;
    } 
}

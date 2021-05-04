package mil.tron.commonapi.appgateway;

import java.util.ArrayList;
import java.util.Collection;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.cache.interceptor.CacheResolver;

import mil.tron.commonapi.service.utility.ResolvePathFromRequest;

public class GatewayCacheResolver implements CacheResolver {

    @Value("${api-prefix.v1}")
    private String apiVersion;

    @Value("${app-sources-prefix}")
    private String appSourcesPrefix;

    @Autowired()
    CacheManager cacheManager;

    public GatewayCacheResolver() {}

    public GatewayCacheResolver(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }
    
    @Override
    public Collection<? extends Cache> resolveCaches(CacheOperationInvocationContext<?> context) {
        Collection<Cache> result = new ArrayList<Cache>();

        for(Object arg : context.getArgs()) {
            if(arg instanceof  HttpServletRequest) {
                String path = ResolvePathFromRequest.resolve((HttpServletRequest) arg, apiVersion + appSourcesPrefix);
                int appSourceAndEndpointSeparator = path.indexOf("/");
                
                // If the trimmed path starts with "/", something didn't parse correctly. 
                // We should put that in the default cache
                if(appSourceAndEndpointSeparator > 0) {
                    String appSourcePath = path.substring(0, appSourceAndEndpointSeparator);
                    result.add(cacheManager.getCache(appSourcePath));
                    break;
                }
                
            }
        }
        if(result.size() == 0) {
            result.add(cacheManager.getCache("default"));
        }
        return result;
    }
    
}

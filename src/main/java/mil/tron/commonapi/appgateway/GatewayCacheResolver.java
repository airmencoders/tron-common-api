package mil.tron.commonapi.appgateway;

import java.util.ArrayList;
import java.util.Collection;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.cache.interceptor.CacheResolver;

import mil.tron.commonapi.ApplicationProperties;
import mil.tron.commonapi.service.utility.ResolvePathFromRequest;

public class GatewayCacheResolver implements CacheResolver {

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ApplicationProperties prefixProperties;

    public GatewayCacheResolver() {}

    public GatewayCacheResolver(
        CacheManager cacheManager, 
        ApplicationProperties prefixProperties
    ) {
        this.cacheManager = cacheManager;
        this.prefixProperties = prefixProperties;
    }
    
    @Override
    public Collection<? extends Cache> resolveCaches(CacheOperationInvocationContext<?> context) {
        Collection<Cache> result = new ArrayList<Cache>();

        for(Object arg : context.getArgs()) {
            if(arg instanceof  HttpServletRequest) {
                String path = ResolvePathFromRequest.resolve((HttpServletRequest) arg, prefixProperties.getCombinedPrefixes());
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

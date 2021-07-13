package mil.tron.commonapi;


import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;

import mil.tron.commonapi.appgateway.GatewayCacheResolver;
import mil.tron.commonapi.appgateway.GatewayKeyGenerator;

@Configuration
@EnableCaching(order = 2147483647)
@ConditionalOnProperty(name = "caching.enabled")
@PropertySource("classpath:application.properties")
public class CacheConfig {
	public static final String SERVICE_ENTITY_CACHE_MANAGER = "serviceEntityCacheManager";
	public static final String APP_SOURCE_DETAILS_CACHE_NAME = "app_source_details_cache";

    @Bean
    public Caffeine<Object, Object> caffeineConfig(@Value("${caching.expire.time:10}") int expireTime, @Value("${caching.expire.unit:MINUTES}") String expireUnit) {
        TimeUnit unit;
        try {
            unit = TimeUnit.valueOf(expireUnit);
        } catch(IllegalArgumentException iaEx) {
            unit = TimeUnit.MINUTES;
        }
        return Caffeine.newBuilder().expireAfterWrite(expireTime, unit);
    }

    @Primary
    @Bean 
    public CacheManager cacheManager(Caffeine<Object, Object> caffeine) {
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCaffeine(caffeine);
        return caffeineCacheManager;
    }
    
    @Bean
    public CacheManager serviceEntityCacheManager() {
        return new ConcurrentMapCacheManager(APP_SOURCE_DETAILS_CACHE_NAME);
    }

    @Bean("gatewayCacheResolver")
    public CacheResolver cacheResolver() {
        return new GatewayCacheResolver();
    }

    @Bean("gatewayKeyGenerator")
    public KeyGenerator keyGenerator() {
        return new GatewayKeyGenerator();
    }

}

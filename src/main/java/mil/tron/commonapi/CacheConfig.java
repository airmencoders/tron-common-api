package mil.tron.commonapi;


import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import mil.tron.commonapi.appgateway.GatewayCacheResolver;
import mil.tron.commonapi.appgateway.GatewayKeyGenerator;

@Configuration
@EnableCaching
@ConditionalOnProperty(name = "caching.enabled")
@PropertySource("classpath:application.properties")
public class CacheConfig {

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

    @Bean 
    public CacheManager cacheManager(Caffeine<Object, Object> caffeine) {
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCaffeine(caffeine);
        return caffeineCacheManager;
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

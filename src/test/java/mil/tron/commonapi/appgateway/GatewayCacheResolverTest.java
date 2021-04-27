package mil.tron.commonapi.appgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.util.Collection;

import javax.servlet.http.HttpServletRequest;
import javax.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.BasicOperation;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.service.AppGatewayService;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties", properties = "caching.enabled=true")
public class GatewayCacheResolverTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppSourceEndpointsBuilder appSourceEndpointsBuilder;

    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private AppGatewayService appGatewayService;

    @Transactional
    @Rollback
    @Test
    public void cacheOnControllerCreatesAndSelectsCorrectCache() throws Exception {
        AppSourceInterfaceDefinition appDef = new AppSourceInterfaceDefinition("Name", "mock.yml",
                "http:////localhost", "mock");
        AppSource appSource = AppSource.builder()
                .name(appDef.getName())
                .openApiSpecFilename(appDef.getOpenApiSpecFilename())
                .appSourcePath(appDef.getAppSourcePath())
                .build();

        byte[] mockResult = "result".getBytes();
        Mockito.when(appGatewayService.sendRequestToAppSource(any(HttpServletRequest.class)))
                .thenReturn(mockResult);
        Mockito.when(appGatewayService.addSourceDefMapping("mock", appDef))
                .thenReturn(true);

        this.appSourceEndpointsBuilder.initializeWithAppSourceDef(appDef, appSource);

        assertFalse(cacheManager.getCacheNames().contains("mock"));

        mockMvc.perform(get("/v1/app/mock/test?abc=123"))
                .andExpect(status().isOk());

        assertTrue(cacheManager.getCacheNames().contains("mock"));
    }

    @Test
    public void cacheOnControllerCreatesAndSelectsCorrectCacheWithNoArgs() throws Exception {
        GatewayCacheResolver gcr = new GatewayCacheResolver(cacheManager);
        
        CacheOperationInvocationContext<BasicOperation> coip = new CacheOperationInvocationContext<BasicOperation>() {
            @Override
            public BasicOperation getOperation() {
                return null;
            }

            @Override
            public Object getTarget() {
                return null;
            }

            @Override
            public Method getMethod() {
                return null;
            }

            @Override
            public Object[] getArgs() {
                return new Object[0];
            }            
        };
        Collection<? extends Cache> caches = gcr.resolveCaches(coip);
        assertEquals(caches.size(), 1);
        assertTrue(caches.stream().map(cache -> cache.getName()).anyMatch(cacheName -> cacheName.equals("default")));
    }

    @Test
    public void handleCacheResolverOnRootOfAppSourceRequest() {
        GatewayCacheResolver gcr = new GatewayCacheResolver(cacheManager);
        
        CacheOperationInvocationContext<BasicOperation> coip = new CacheOperationInvocationContext<BasicOperation>() {
            @Override
            public BasicOperation getOperation() {
                return null;
            }

            @Override
            public Object getTarget() {
                return null;
            }

            @Override
            public Method getMethod() {
                return null;
            }

            @Override
            public Object[] getArgs() {
                HttpServletRequest request = get("/app/mock").buildRequest(null);
                return new Object[] { request };
            }            
        };
        Collection<? extends Cache> caches = gcr.resolveCaches(coip);
        assertEquals(caches.size(), 1);
        assertTrue(caches.stream().map(cache -> cache.getName()).anyMatch(cacheName -> cacheName.equals("default")));
    }
}

package mil.tron.commonapi.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;

import javax.transaction.Transactional;

import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.repository.DashboardUserRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.Rollback;
import org.springframework.web.bind.annotation.RequestMethod;

import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.appsource.AppEndpoint;
import mil.tron.commonapi.entity.appsource.AppEndpointPriv;
import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.appsource.AppEndpointPrivRepository;
import mil.tron.commonapi.repository.appsource.AppEndpointRepository;
import mil.tron.commonapi.repository.appsource.AppSourceRepository;

@SpringBootTest
public class AccessCheckAppSourceImplTest {
        
    @Autowired
    AppSourceRepository sourceRepo;

    @Autowired
    AppEndpointRepository endpointRepo;

    @Autowired
    AppEndpointPrivRepository endpointPrivRepo;

    @Autowired
    AppClientUserRespository clientRepo;

    @Autowired
    DashboardUserRepository dashboardUserRepository;

    @Nested
    class ByAppSource {
        @Test
        @Transactional
        @Rollback
        @WithMockUser(username="test-client-username", authorities = "appsource/endpoint_GET")
        public void passAuthenticationCheck() {
            AccessCheckAppSourceImpl accessCheckImpl = new AccessCheckAppSourceImpl(sourceRepo, clientRepo,
                    endpointPrivRepo);
            AppSource appSource = sourceRepo.save(
                AppSource.builder()
                    .id(UUID.randomUUID())
                    .name("Name")
                    .appSourcePath("appsource")
                    .build());
            assertTrue(accessCheckImpl.checkByAppSourceId(SecurityContextHolder.getContext().getAuthentication(), appSource.getId().toString()));
        }

        @Test        
        @Transactional
        @Rollback
        @WithMockUser(username="test-client-username", authorities = "appsource2/endpoint_GET")
        public void failAuthenticationCheck() {
            AccessCheckAppSourceImpl accessCheckImpl = new AccessCheckAppSourceImpl(sourceRepo, clientRepo,
                    endpointPrivRepo);
            AppSource appSource = sourceRepo.save(
                AppSource.builder()
                    .id(UUID.randomUUID())
                    .name("Name")
                    .appSourcePath("appsource")
                    .build());
            assertFalse(accessCheckImpl.checkByAppSourceId(SecurityContextHolder.getContext().getAuthentication(), appSource.getId().toString()));
        }

        @Test
        @Transactional
        @Rollback
        @WithMockUser(username="test-client-username", authorities = "appsource/endpoint_GET")
        public void nullAuthenticationCheck() {
            AccessCheckAppSourceImpl accessCheckImpl = new AccessCheckAppSourceImpl(sourceRepo, clientRepo,
                    endpointPrivRepo);
            AppSource appSource = sourceRepo.save(
                AppSource.builder()
                    .id(UUID.randomUUID())
                    .name("Name")
                    .appSourcePath("appsource")
                    .build());
            assertFalse(accessCheckImpl.checkByAppSourceId(null, appSource.getId().toString()));
        }

        @Test
        @Transactional
        @Rollback
        @WithMockUser(username="test-client-username", authorities = "appsource/endpoint_GET")
        public void nullIdCheck() {
            AccessCheckAppSourceImpl accessCheckImpl = new AccessCheckAppSourceImpl(sourceRepo, clientRepo,
                    endpointPrivRepo);
            assertFalse(accessCheckImpl.checkByAppSourceId(SecurityContextHolder.getContext().getAuthentication(), null));
        }
        
        @Test
        @Transactional
        @Rollback
        @WithMockUser(username="test-client-username", authorities = "appsource/endpoint_GET")
        public void badIdCheck() {
            AccessCheckAppSourceImpl accessCheckImpl = new AccessCheckAppSourceImpl(sourceRepo, clientRepo,
                    endpointPrivRepo);
            assertFalse(accessCheckImpl.checkByAppSourceId(SecurityContextHolder.getContext().getAuthentication(), "test"));
        }

        @Test
        @Transactional
        @Rollback
        @WithMockUser(username="test-client-username", authorities = "appsource/endpoint_GET")
        public void NoAppSourceExistsCheck() {
            AccessCheckAppSourceImpl accessCheckImpl = new AccessCheckAppSourceImpl(sourceRepo, clientRepo,
                    endpointPrivRepo);
            assertFalse(accessCheckImpl.checkByAppSourceId(SecurityContextHolder.getContext().getAuthentication(), UUID.randomUUID().toString()));
        }

        @Test
        @Transactional
        @Rollback
        @WithMockUser(username="test-client-username", authorities = "appsource/endpoint_GET")
        public void UserValidAppClientDev() {
            AccessCheckAppSourceImpl accessCheckImpl = new AccessCheckAppSourceImpl(sourceRepo, clientRepo,
                    endpointPrivRepo);
            UUID appSourceId = UUID.randomUUID();
            AppSource appSource = sourceRepo.save(
                    AppSource.builder()
                            .id(appSourceId)
                            .name("Name")
                            .appSourcePath("appsource")
                            .build());
            DashboardUser dashboardUser = dashboardUserRepository.save(DashboardUser.builder()
                    .id(UUID.randomUUID())
                    .email("test@email.com")
                    .build());
            AppClientUser appClient = clientRepo.saveAndFlush(AppClientUser.builder()
                    .id(UUID.randomUUID())
                    .appClientDevelopers(Set.of(dashboardUser))
                    .build());
            AppEndpoint appEndpoint = endpointRepo.save(AppEndpoint.builder()
                    .id(UUID.randomUUID())
                    .appSource(appSource)
                    .build());
            endpointPrivRepo.saveAndFlush(AppEndpointPriv.builder()
                    .id(UUID.randomUUID())
                    .appSource(appSource)
                    .appClientUser(appClient)
                    .appEndpoint(appEndpoint)
                    .build());
            Authentication authentication = new TestingAuthenticationToken("", "test@email.com");
            assertTrue(accessCheckImpl.checkByAppSourceId(authentication, appSource.getId().toString()));
        }
        @Test
        @Transactional
        @Rollback
        @WithMockUser(username="test-client-username", authorities = "appsource/endpoint_GET")
        public void UserInvalidAppClientDev() {
            AccessCheckAppSourceImpl accessCheckImpl = new AccessCheckAppSourceImpl(sourceRepo, clientRepo,
                    endpointPrivRepo);
            UUID appSourceId = UUID.randomUUID();
            AppSource appSource = sourceRepo.save(
                    AppSource.builder()
                            .id(appSourceId)
                            .name("Name")
                            .appSourcePath("appsource")
                            .build());
            DashboardUser dashboardUser = dashboardUserRepository.save(DashboardUser.builder()
                    .id(UUID.randomUUID())
                    .email("test@email.com")
                    .build());
            AppClientUser appClient = clientRepo.saveAndFlush(AppClientUser.builder()
                    .id(UUID.randomUUID())
                    .appClientDevelopers(Set.of(dashboardUser))
                    .build());
            AppEndpoint appEndpoint = endpointRepo.save(AppEndpoint.builder()
                    .id(UUID.randomUUID())
                    .appSource(appSource)
                    .build());
            endpointPrivRepo.saveAndFlush(AppEndpointPriv.builder()
                    .id(UUID.randomUUID())
                    .appSource(appSource)
                    .appClientUser(appClient)
                    .appEndpoint(appEndpoint)
                    .build());
            Authentication authentication = new TestingAuthenticationToken("", "test-fail@email.com");
            assertFalse(accessCheckImpl.checkByAppSourceId(authentication, appSource.getId().toString()));
        }
    }

    @Nested
    class ByEndpointPriv {
        @Test
        @Transactional
        @Rollback
        @WithMockUser(username="test-client-username", authorities = "appsource/endpoint_GET")
        public void passAuthenticationCheck() {
            AccessCheckAppSourceImpl accessCheckImpl = new AccessCheckAppSourceImpl(sourceRepo, clientRepo,
                    endpointPrivRepo);
            AppClientUser client = clientRepo.save(
                AppClientUser.builder()
                    .id(UUID.randomUUID())
                    .name("test-client-username")
                    .build()
            );
            AppSource appSource = sourceRepo.save(
                AppSource.builder()
                    .id(UUID.randomUUID())
                    .name("Name")
                    .appSourcePath("appsource")
                    .build());
            AppEndpoint endpoint = endpointRepo.save(
                AppEndpoint.builder()
                    .id(UUID.randomUUID())
                    .path("endpoint")
                    .method(RequestMethod.GET)
                    .appSource(appSource)
                    .build()
            );
            AppEndpointPriv priv = endpointPrivRepo.save(
                AppEndpointPriv.builder()
                    .id(UUID.randomUUID())
                    .appEndpoint(endpoint)
                    .appSource(appSource)
                    .appClientUser(client)
                    .build() 
            );
            assertTrue(accessCheckImpl.checkByAppEndpointPrivId(SecurityContextHolder.getContext().getAuthentication(), priv.getId().toString()));
        }

        @Test        
        @Transactional
        @Rollback
        @WithMockUser(username="test-client-username", authorities = "appsource2/endpoint_GET")
        public void failAuthenticationCheck() {
            AccessCheckAppSourceImpl accessCheckImpl = new AccessCheckAppSourceImpl(sourceRepo, clientRepo,
                    endpointPrivRepo);
            AppClientUser client = clientRepo.save(
                AppClientUser.builder()
                    .id(UUID.randomUUID())
                    .name("test-client-username")
                    .build()
            );
            AppSource appSource = sourceRepo.save(
                AppSource.builder()
                    .id(UUID.randomUUID())
                    .name("Name")
                    .appSourcePath("appsource")
                    .build());
            AppEndpoint endpoint = endpointRepo.save(
                AppEndpoint.builder()
                    .id(UUID.randomUUID())
                    .path("endpoint")
                    .method(RequestMethod.GET)
                    .appSource(appSource)
                    .build()
            );
            AppEndpointPriv priv = endpointPrivRepo.save(
                AppEndpointPriv.builder()
                    .id(UUID.randomUUID())
                    .appEndpoint(endpoint)
                    .appSource(appSource)
                    .appClientUser(client)
                    .build() 
            );
            assertFalse(accessCheckImpl.checkByAppEndpointPrivId(SecurityContextHolder.getContext().getAuthentication(), priv.getId().toString()));
        }

        @Test
        @Transactional
        @Rollback
        @WithMockUser(username="test-client-username", authorities = "appsource/endpoint_GET")
        public void nullAuthenticationCheck() {
            AccessCheckAppSourceImpl accessCheckImpl = new AccessCheckAppSourceImpl(sourceRepo, clientRepo,
                    endpointPrivRepo);
            AppClientUser client = clientRepo.save(
                AppClientUser.builder()
                    .id(UUID.randomUUID())
                    .name("test-client-username")
                    .build()
            );
            AppSource appSource = sourceRepo.save(
                AppSource.builder()
                    .id(UUID.randomUUID())
                    .name("Name")
                    .appSourcePath("appsource")
                    .build());
            AppEndpoint endpoint = endpointRepo.save(
                AppEndpoint.builder()
                    .id(UUID.randomUUID())
                    .path("endpoint")
                    .method(RequestMethod.GET)
                    .appSource(appSource)
                    .build()
            );
            AppEndpointPriv priv = endpointPrivRepo.save(
                AppEndpointPriv.builder()
                    .id(UUID.randomUUID())
                    .appEndpoint(endpoint)
                    .appSource(appSource)
                    .appClientUser(client)
                    .build() 
            );
            assertFalse(accessCheckImpl.checkByAppEndpointPrivId(null, priv.getId().toString()));
        }

        @Test
        @Transactional
        @Rollback
        @WithMockUser(username="test-client-username", authorities = "appsource/endpoint_GET")
        public void nullIdCheck() {
            AccessCheckAppSourceImpl accessCheckImpl = new AccessCheckAppSourceImpl(sourceRepo, clientRepo,
                    endpointPrivRepo);
            assertFalse(accessCheckImpl.checkByAppEndpointPrivId(SecurityContextHolder.getContext().getAuthentication(), null));
        }
        
        @Test
        @Transactional
        @Rollback
        @WithMockUser(username="test-client-username", authorities = "appsource/endpoint_GET")
        public void badIdCheck() {
            AccessCheckAppSourceImpl accessCheckImpl = new AccessCheckAppSourceImpl(sourceRepo, clientRepo,
                    endpointPrivRepo);
            assertFalse(accessCheckImpl.checkByAppEndpointPrivId(SecurityContextHolder.getContext().getAuthentication(), "test"));
        }

        @Test
        @Transactional
        @Rollback
        @WithMockUser(username="test-client-username", authorities = "appsource/endpoint_GET")
        public void NoAppSourceExistsCheck() {
            AccessCheckAppSourceImpl accessCheckImpl = new AccessCheckAppSourceImpl(sourceRepo, clientRepo,
                    endpointPrivRepo);
            assertFalse(accessCheckImpl.checkByAppEndpointPrivId(SecurityContextHolder.getContext().getAuthentication(), UUID.randomUUID().toString()));
        }
    }
}

package mil.tron.commonapi.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;

import javax.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.DashboardUserRepository;

@SpringBootTest
class AccessCheckEventRequestLogImplTest {
	private static final String appClientDevEmail = "app@client.dev";
	private static final String appClientName = "Test App Client";
	private static final String dashboardAdminEmail = "dashboard@admin.test";

	@Autowired
	private AppClientUserRespository appClientUserRepo;
	
	@Autowired
	private DashboardUserRepository dashboardUserRepo;
	
	private AccessCheckEventRequestLogImpl accessCheck;
	
	private AppClientUser appClientUser;
	
	@BeforeEach
	void setup() {
		accessCheck = new AccessCheckEventRequestLogImpl(appClientUserRepo);

		appClientUserRepo.deleteAll();
		dashboardUserRepo.deleteAll();
		
		DashboardUser dashboardUser = dashboardUserRepo.save(DashboardUser
				.builder()
				.id(UUID.randomUUID())
				.email(appClientDevEmail)
				.emailAsLower(appClientDevEmail.toLowerCase())
				.build());
		
		appClientUser = appClientUserRepo.save(AppClientUser
				.builder()
				.appClientDevelopers(Set.of(dashboardUser))
				.availableAsAppClient(true)
				.id(UUID.randomUUID())
				.name(appClientName)
				.name(appClientName.toLowerCase())
				.build());
	}
	
	@Test
	void isRequesterAppClientOrDeveloper_shouldReturnFalse_whenAuthNull() {
		assertThat(accessCheck.isRequesterAppClientOrDeveloper(null, UUID.randomUUID())).isFalse();
	}
	
	@Test
	@WithMockUser(username=dashboardAdminEmail)
	void isRequesterAppClientOrDeveloper_shouldReturnFalse_whenAppClientIdNull() {
		assertThat(accessCheck.isRequesterAppClientOrDeveloper(SecurityContextHolder.getContext().getAuthentication(), null)).isFalse();
	}
	
	@Test
	@WithMockUser(username=appClientName)
	void isRequesterAppClientOrDeveloper_shouldReturnFalse_whenAppClientDoesNotExist() {
		assertThat(accessCheck.isRequesterAppClientOrDeveloper(SecurityContextHolder.getContext().getAuthentication(), UUID.randomUUID())).isFalse();
	}
	
	@Test
	@Transactional
	@WithMockUser(username="Another App Client")
	void isRequesterAppClientOrDeveloper_shouldReturnFalse_whenAuthIsAppClient_andIsNotSelf() {
		assertThat(accessCheck.isRequesterAppClientOrDeveloper(SecurityContextHolder.getContext().getAuthentication(), appClientUser.getId())).isFalse();
	}
	
	@Test
	@Transactional
	@WithMockUser(username="non_app-client_dev@test.com")
	void isRequesterAppClientOrDeveloper_shouldReturnFalse_whenAuthIsDashboardUser_andIsNotAppClientDev() {
		assertThat(accessCheck.isRequesterAppClientOrDeveloper(SecurityContextHolder.getContext().getAuthentication(), appClientUser.getId())).isFalse();
	}
	
	@Test
	@WithMockUser(username=appClientName)
	void isRequesterAppClientOrDeveloper_shouldReturnTrue_whenAuthIsAppClient_andIsSelf() {
		assertThat(accessCheck.isRequesterAppClientOrDeveloper(SecurityContextHolder.getContext().getAuthentication(), appClientUser.getId())).isTrue();
	}
	
	@Test
	@Transactional
	@WithMockUser(username=appClientDevEmail)
	void isRequesterAppClientOrDeveloper_shouldReturnTrue_whenAuthIsDashboardUser_andIsAppClientDev() {
		assertThat(accessCheck.isRequesterAppClientOrDeveloper(SecurityContextHolder.getContext().getAuthentication(), appClientUser.getId())).isTrue();
	}
}

package mil.tron.commonapi.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

import mil.tron.commonapi.service.documentspace.DocumentSpacePrivilegeService;
import mil.tron.commonapi.service.documentspace.DocumentSpacePrivilegeType;
import mil.tron.commonapi.service.documentspace.DocumentSpaceServiceImpl;

@SpringBootTest(properties = { "minio.enabled=true" })
class AccessCheckDocumentSpaceImplTest {
	private static final String ID = "912d7abd-1534-4160-bcf9-ae97c9e003f6";
	private static final UUID DOCUMENT_SPACE_ID = UUID.fromString(ID);
	
	@Autowired
	private DocumentSpacePrivilegeService documentSpacePrivilegeService;

	private AccessCheckDocumentSpaceImpl accessCheck;
	
	@BeforeEach
	void setup() {
		accessCheck = new AccessCheckDocumentSpaceImpl(documentSpacePrivilegeService);
	}
	
	@Nested
	class ReadAccessTest {
		@Test
		@WithMockDocumentSpaceUser(documentSpaceId = ID, withPrivileges = { DocumentSpacePrivilegeType.READ })
		void readAccess_shouldReturnTrue_whenAuthUserHasReadPrivilege() {
			assertThat(accessCheck.hasReadAccess(SecurityContextHolder.getContext().getAuthentication(), DOCUMENT_SPACE_ID)).isTrue();
		}
		
		@Test
		@WithMockUser(authorities = {"DASHBOARD_ADMIN"})
		void readAccess_shouldReturnTrue_whenAuthUserIsDashboardAdmin() {
			assertThat(accessCheck.hasReadAccess(SecurityContextHolder.getContext().getAuthentication(), DOCUMENT_SPACE_ID)).isTrue();
		}
		
		@Test
		@WithMockDocumentSpaceUser(documentSpaceId = ID, withPrivileges = { DocumentSpacePrivilegeType.WRITE })
		void readAccess_shouldReturnFalse_whenAuthUserDoesNotHaveReadPrivilege() {
			assertThat(accessCheck.hasReadAccess(SecurityContextHolder.getContext().getAuthentication(), DOCUMENT_SPACE_ID)).isFalse();
		}
		
		@Test
		void readAccess_shouldReturnFalse_whenNoAuthUser() {
			assertThat(accessCheck.hasReadAccess(SecurityContextHolder.getContext().getAuthentication(), DOCUMENT_SPACE_ID)).isFalse();
		}
		
		@Test
		@WithMockDocumentSpaceUser(documentSpaceId = ID, withPrivileges = { DocumentSpacePrivilegeType.READ })
		void readAccess_shouldReturnFalse_whenInvalidId() {
			assertThat(accessCheck.hasReadAccess(SecurityContextHolder.getContext().getAuthentication(), null)).isFalse();
		}
	}
	
	@Nested
	class WriteAccessTest {
		@Test
		@WithMockDocumentSpaceUser(documentSpaceId = ID, withPrivileges = { DocumentSpacePrivilegeType.WRITE })
		void writeAccess_shouldReturnTrue_whenAuthUserHasWritePrivilege() {
			assertThat(accessCheck.hasWriteAccess(SecurityContextHolder.getContext().getAuthentication(), DOCUMENT_SPACE_ID)).isTrue();
		}
		
		@Test
		@WithMockUser(authorities = {"DASHBOARD_ADMIN"})
		void writeAccess_shouldReturnTrue_whenAuthUserIsDashboardAdmin() {
			assertThat(accessCheck.hasWriteAccess(SecurityContextHolder.getContext().getAuthentication(), DOCUMENT_SPACE_ID)).isTrue();
		}
		
		@Test
		@WithMockDocumentSpaceUser(documentSpaceId = ID, withPrivileges = { DocumentSpacePrivilegeType.READ })
		void writeAccess_shouldReturnFalse_whenAuthUserDoesNotHaveWritePrivilege() {
			assertThat(accessCheck.hasWriteAccess(SecurityContextHolder.getContext().getAuthentication(), DOCUMENT_SPACE_ID)).isFalse();
		}
		
		@Test
		void writeAccess_shouldReturnFalse_whenNoAuthUser() {
			assertThat(accessCheck.hasWriteAccess(SecurityContextHolder.getContext().getAuthentication(), DOCUMENT_SPACE_ID)).isFalse();
		}
		
		@Test
		@WithMockDocumentSpaceUser(documentSpaceId = ID, withPrivileges = { DocumentSpacePrivilegeType.WRITE })
		void writeAccess_shouldReturnFalse_whenInvalidId() {
			assertThat(accessCheck.hasWriteAccess(SecurityContextHolder.getContext().getAuthentication(), null)).isFalse();
		}
	}
	
	@Nested
	class MembershipAccessTest {
		@Test
		@WithMockDocumentSpaceUser(documentSpaceId = ID, withPrivileges = { DocumentSpacePrivilegeType.MEMBERSHIP })
		void membershipAccess_shouldReturnTrue_whenAuthUserHasMembershipPrivilege() {
			assertThat(accessCheck.hasMembershipAccess(SecurityContextHolder.getContext().getAuthentication(), DOCUMENT_SPACE_ID)).isTrue();
		}
		
		@Test
		@WithMockUser(authorities = {"DASHBOARD_ADMIN"})
		void membershipAccess_shouldReturnTrue_whenAuthUserIsDashboardAdmin() {
			assertThat(accessCheck.hasMembershipAccess(SecurityContextHolder.getContext().getAuthentication(), DOCUMENT_SPACE_ID)).isTrue();
		}
		
		@Test
		@WithMockDocumentSpaceUser(documentSpaceId = ID, withPrivileges = { DocumentSpacePrivilegeType.READ })
		void membershipAccess_shouldReturnFalse_whenAuthUserDoesNotHaveMembershipPrivilege() {
			assertThat(accessCheck.hasMembershipAccess(SecurityContextHolder.getContext().getAuthentication(), DOCUMENT_SPACE_ID)).isFalse();
		}
		
		@Test
		void membershipAccess_shouldReturnFalse_whenNoAuthUser() {
			assertThat(accessCheck.hasMembershipAccess(SecurityContextHolder.getContext().getAuthentication(), DOCUMENT_SPACE_ID)).isFalse();
		}
		
		@Test
		@WithMockDocumentSpaceUser(documentSpaceId = ID, withPrivileges = { DocumentSpacePrivilegeType.MEMBERSHIP })
		void membershipAccess_shouldReturnFalse_whenInvalidId() {
			assertThat(accessCheck.hasMembershipAccess(SecurityContextHolder.getContext().getAuthentication(), null)).isFalse();
		}
	}
	
	@Nested
	class HasDocumentSpaceAccessTest {
		@Test
		@WithMockUser(authorities = {DocumentSpaceServiceImpl.DOCUMENT_SPACE_USER_PRIVILEGE})
		void hasDocumentSpaceAccess_shouldReturnTrue_whenAuthUserHasDocumentSpacePrivilege() {
			assertThat(accessCheck.hasDocumentSpaceAccess(SecurityContextHolder.getContext().getAuthentication())).isTrue();
		}
		
		@Test
		@WithMockUser(authorities = {"DASHBOARD_ADMIN"})
		void hasDocumentSpaceAccess_shouldReturnTrue_whenAuthUserIsDashboardAdmin() {
			assertThat(accessCheck.hasDocumentSpaceAccess(SecurityContextHolder.getContext().getAuthentication())).isTrue();
		}
		
		@Test
		@WithMockUser()
		void hasDocumentSpaceAccess_shouldReturnFalse_whenAuthUserIsNotDashboardAdminAndIsNotDocumentSpacePrivilege() {
			assertThat(accessCheck.hasDocumentSpaceAccess(SecurityContextHolder.getContext().getAuthentication())).isFalse();
		}

		@Test
		void hasDocumentSpaceAccess_shouldReturnFalse_whenNoAuthUser() {
			assertThat(accessCheck.hasDocumentSpaceAccess(SecurityContextHolder.getContext().getAuthentication())).isFalse();
		}
	}
}

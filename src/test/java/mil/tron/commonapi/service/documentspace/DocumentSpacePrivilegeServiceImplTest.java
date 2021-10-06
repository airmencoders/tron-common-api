package mil.tron.commonapi.service.documentspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import mil.tron.commonapi.dto.documentspace.DocumentSpacePrivilegeDto;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.documentspace.DocumentSpace;
import mil.tron.commonapi.entity.documentspace.DocumentSpacePrivilege;
import mil.tron.commonapi.repository.documentspace.DocumentSpacePrivilegeRepository;
import mil.tron.commonapi.service.DashboardUserService;

@ExtendWith(MockitoExtension.class)
class DocumentSpacePrivilegeServiceImplTest {
	@Mock
	private DocumentSpacePrivilegeRepository documentSpacePrivilegeRepo;
	
	@Mock
	private DashboardUserService dashboardUserService;
	
	@InjectMocks
	private DocumentSpacePrivilegeServiceImpl documentSpacePrivilegeService;
	
	private DocumentSpace documentSpace;
	private DashboardUser dashboardUser;
	private AppClientUser appClientUser;
	
	@BeforeEach
	void setup() {
		UUID id = UUID.randomUUID();
		
		EnumMap<DocumentSpacePrivilegeType, DocumentSpacePrivilege> documentSpacePrivilegesMap = new EnumMap<>(
				DocumentSpacePrivilegeType.class);
		documentSpacePrivilegesMap.put(DocumentSpacePrivilegeType.READ,
				DocumentSpacePrivilege.builder().id(UUID.randomUUID())
						.name(String.format("DOCUMENT_SPACE_%s_%s", id.toString(), DocumentSpacePrivilegeType.READ))
						.type(DocumentSpacePrivilegeType.READ).build());

		documentSpacePrivilegesMap.put(DocumentSpacePrivilegeType.WRITE,
				DocumentSpacePrivilege.builder().id(UUID.randomUUID())
						.name(String.format("DOCUMENT_SPACE_%s_%s", id.toString(), DocumentSpacePrivilegeType.WRITE))
						.type(DocumentSpacePrivilegeType.WRITE).build());

		documentSpacePrivilegesMap.put(DocumentSpacePrivilegeType.MEMBERSHIP,
				DocumentSpacePrivilege.builder().id(UUID.randomUUID())
						.name(String.format("DOCUMENT_SPACE_%s_%s", id.toString(), DocumentSpacePrivilegeType.MEMBERSHIP))
						.type(DocumentSpacePrivilegeType.MEMBERSHIP).build());
		
		documentSpace = DocumentSpace.builder()
				.id(id)
				.name("Test Document Space")
				.privileges(documentSpacePrivilegesMap)
				.build();
		
		dashboardUser = DashboardUser.builder()
				.id(UUID.randomUUID())
				.email("dashboard@user.com")
				.emailAsLower("dashboard@user.com")
				.build();
		
		appClientUser = AppClientUser.builder()
				.id(UUID.randomUUID())
				.name("Test App Client")
				.nameAsLower("test app client")
				.availableAsAppClient(true)
				.build();
		
		documentSpace.addDashboardUser(dashboardUser);
		documentSpace.addAppClientUser(appClientUser);
		
		dashboardUser.addDocumentSpacePrivilege(documentSpace.getPrivileges().get(DocumentSpacePrivilegeType.READ));
		appClientUser.addDocumentSpacePrivilege(documentSpace.getPrivileges().get(DocumentSpacePrivilegeType.READ));
	}
	
	@Test
	void shouldDeleteAllPrivilegesBelongingToDocumentSpace() {
		assertThat(documentSpace.getPrivileges()).isNotEmpty();
		assertThat(dashboardUser.getDocumentSpacePrivileges()).isNotEmpty();
		assertThat(appClientUser.getDocumentSpacePrivileges()).isNotEmpty();
		
		documentSpacePrivilegeService.deleteAllPrivilegesBelongingToDocumentSpace(documentSpace);
		
		assertThat(documentSpace.getPrivileges()).isEmpty();
		assertThat(dashboardUser.getDocumentSpacePrivileges()).isEmpty();
		assertThat(appClientUser.getDocumentSpacePrivileges()).isEmpty();
	}
	
	@Test
	void shouldCreateAllPrivilegesForANewDocumentSpace() {
		List<DocumentSpacePrivilege> privileges = new ArrayList<>(documentSpace.getPrivileges().values());
		Mockito.when(documentSpacePrivilegeRepo.saveAll(Mockito.anyList())).thenReturn(privileges);
		
		List<DocumentSpacePrivilege> createdPrivileges = documentSpacePrivilegeService.createPrivilegesForNewSpace(documentSpace.getId());
		assertThat(createdPrivileges).containsExactlyInAnyOrderElementsOf(privileges);
	}
	
	@Test
	void shouldCreatePrivilegeName() {
		String privilegeName = documentSpacePrivilegeService.createPrivilegeName(documentSpace.getId(), DocumentSpacePrivilegeType.READ);
		assertThat(privilegeName).isEqualTo(String.format("DOCUMENT_SPACE_%s_%s", documentSpace.getId().toString(), DocumentSpacePrivilegeType.READ));
	}
	
	@Test
	void shouldAddPrivilegeToDashboardUser() {
		DocumentSpacePrivilege read = documentSpace.getPrivileges().get(DocumentSpacePrivilegeType.READ);
		DocumentSpacePrivilege write = documentSpace.getPrivileges().get(DocumentSpacePrivilegeType.WRITE);
		DocumentSpacePrivilege memberships = documentSpace.getPrivileges().get(DocumentSpacePrivilegeType.MEMBERSHIP);
		
		assertThat(dashboardUser.getDocumentSpacePrivileges()).containsOnly(read);
		
		documentSpacePrivilegeService.addPrivilegesToDashboardUser(dashboardUser, documentSpace,
				new ArrayList<>(Arrays.asList(DocumentSpacePrivilegeType.WRITE, DocumentSpacePrivilegeType.MEMBERSHIP, DocumentSpacePrivilegeType.READ)));
		
		assertThat(dashboardUser.getDocumentSpacePrivileges()).contains(read, write, memberships);
		
		assertThat(read.getDashboardUsers()).contains(dashboardUser);
		assertThat(write.getDashboardUsers()).contains(dashboardUser);
		assertThat(memberships.getDashboardUsers()).contains(dashboardUser);
	}
	
	@Test
	void shouldThrow_whenAddingNullPrivilegeToDashboardUser() {
		documentSpace.getPrivileges().remove(DocumentSpacePrivilegeType.WRITE);
		
		assertThatThrownBy(() -> documentSpacePrivilegeService.addPrivilegesToDashboardUser(dashboardUser,
				documentSpace, new ArrayList<>(Arrays.asList(DocumentSpacePrivilegeType.WRITE))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Could not add privileges to user");
	}
	
	@Test
	void shouldRemovePrivilegeFromDashboardUser() {
		DocumentSpacePrivilege read = documentSpace.getPrivileges().get(DocumentSpacePrivilegeType.READ);
		
		assertThat(dashboardUser.getDocumentSpacePrivileges()).containsOnly(read);
		
		documentSpacePrivilegeService.removePrivilegesFromDashboardUser(dashboardUser, documentSpace,
				new ArrayList<>(Arrays.asList(DocumentSpacePrivilegeType.READ)));
		
		assertThat(dashboardUser.getDocumentSpacePrivileges()).doesNotContain(read);
		assertThat(read.getDashboardUsers()).doesNotContain(dashboardUser);
	}
	
	@Test
	void shouldThrow_whenRemovingNullPrivilegeFromDashboardUser() {
		documentSpace.getPrivileges().remove(DocumentSpacePrivilegeType.READ);
		
		assertThatThrownBy(() -> documentSpacePrivilegeService.removePrivilegesFromDashboardUser(dashboardUser,
				documentSpace, new ArrayList<>(Arrays.asList(DocumentSpacePrivilegeType.READ))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Could not remove privileges from user");
	}
	
	@Test
	void shouldCreateDashboardUserWithPrivileges() {
		DashboardUser createdDashboardUser = DashboardUser.builder()
				.email("test@email.com")
				.emailAsLower("test@email.com")
				.build();
		
		Mockito.when(dashboardUserService.createDashboardUserOrReturnExisting(Mockito.anyString())).thenReturn(createdDashboardUser);
		
		DashboardUser dashboardUserWithPrivileges = documentSpacePrivilegeService.createDashboardUserWithPrivileges(
				createdDashboardUser.getEmail(), documentSpace, new ArrayList<>(Arrays.asList(DocumentSpacePrivilegeType.READ)));
		
		DocumentSpacePrivilege read = documentSpace.getPrivileges().get(DocumentSpacePrivilegeType.READ);
		
		assertThat(dashboardUserWithPrivileges.getDocumentSpacePrivileges()).contains(read);
		assertThat(read.getDashboardUsers()).contains(dashboardUserWithPrivileges);
	}
	
	@Test
	void shouldConvertDocumentSpacePrivilegeToDto() {
		DocumentSpacePrivilege privilege = DocumentSpacePrivilege.builder()
				.id(UUID.randomUUID())
				.name("test privilege")
				.type(DocumentSpacePrivilegeType.READ)
				.build();
		
		DocumentSpacePrivilegeDto dto = DocumentSpacePrivilegeDto.builder()
				.id(privilege.getId())
				.name(privilege.getName())
				.type(privilege.getType())
				.build();
		
		assertThat(documentSpacePrivilegeService.convertToDto(privilege)).isEqualTo(dto);
	}
}
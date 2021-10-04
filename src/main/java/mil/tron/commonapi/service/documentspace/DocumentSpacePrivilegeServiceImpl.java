package mil.tron.commonapi.service.documentspace;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import mil.tron.commonapi.dto.documentspace.DocumentSpacePrivilegeDto;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.documentspace.DocumentSpace;
import mil.tron.commonapi.entity.documentspace.DocumentSpacePrivilege;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.documentspace.DocumentSpacePrivilegeRepository;
import mil.tron.commonapi.service.DashboardUserService;

@Slf4j
@Service
public class DocumentSpacePrivilegeServiceImpl implements DocumentSpacePrivilegeService {
	private static final ModelMapper MODEL_MAPPER = new DtoMapper();
	
	private final DocumentSpacePrivilegeRepository documentSpacePrivilegeRepository;
	private final DashboardUserService dashboardUserService;
	
	public DocumentSpacePrivilegeServiceImpl(DocumentSpacePrivilegeRepository documentSpacePrivilegeRepository, DashboardUserService dashboardUserService) {
		this.documentSpacePrivilegeRepository = documentSpacePrivilegeRepository;
		this.dashboardUserService = dashboardUserService;
	}
	
	@Override
	public void deleteAllPrivilegesBelongingToDocumentSpace(DocumentSpace documentSpace) {
		Map<DocumentSpacePrivilegeType, DocumentSpacePrivilege> privileges = documentSpace.getPrivileges();
		List<DocumentSpacePrivilege> privilegesToDelete = new ArrayList<>();
		privileges.forEach((privilegeType, privilege) -> {
			for (DashboardUser dashboardUser : new HashSet<>(privilege.getDashboardUsers())) {
				privilege.removeDashboardUser(dashboardUser);
			}
			
			for (AppClientUser appClientUser : new HashSet<>(privilege.getAppClientUsers())) {
				privilege.removeAppClientUser(appClientUser);
			}
			
			privilegesToDelete.add(privilege);
		});
		
		documentSpace.getPrivileges().clear();
 		documentSpacePrivilegeRepository.deleteAll(privilegesToDelete);
	}

	@Override
	public List<DocumentSpacePrivilege> createPrivilegesForNewSpace(UUID documentSpaceId) {

		List<DocumentSpacePrivilege> privilegesToAdd = new ArrayList<>();
		
		DocumentSpacePrivilegeType[] documentSpacePrivilegeTypes = DocumentSpacePrivilegeType.values();
		
		for (int i = 0; i < documentSpacePrivilegeTypes.length; i++) {
			DocumentSpacePrivilegeType currentType = documentSpacePrivilegeTypes[i];
			privilegesToAdd.add(buildDocumentSpacePrivilege(createPrivilegeName(documentSpaceId, currentType), currentType));
		}
		
		return documentSpacePrivilegeRepository.saveAll(privilegesToAdd);
	}

	@Override
	public String createPrivilegeName(UUID documentSpaceId, DocumentSpacePrivilegeType privilegeType) {
		return String.format("DOCUMENT_SPACE_%s_%s", documentSpaceId.toString(), privilegeType);
	}
	
	@Override
	public void addPrivilegesToDashboardUser(DashboardUser dashboardUser, DocumentSpace documentSpace,
			List<DocumentSpacePrivilegeType> privilegesToAdd) throws IllegalArgumentException {

		Set<DocumentSpacePrivilege> existingDashboardUserPrivileges = dashboardUser.getDocumentSpacePrivileges();
		existingDashboardUserPrivileges
				.forEach(existingPrivilege -> privilegesToAdd.remove(existingPrivilege.getType()));

		Map<DocumentSpacePrivilegeType, DocumentSpacePrivilege> documentSpacePrivileges = documentSpace.getPrivileges();
		List<DocumentSpacePrivilege> privilegesToSave = new ArrayList<>();

		privilegesToAdd.forEach(type -> {
			DocumentSpacePrivilege privilege = documentSpacePrivileges.get(type);

			if (privilege == null) {
				log.error(String.format(
						"Error adding Document Space privileges to user. Document Space: %s (%s) missing necessary privilege type: %s",
						documentSpace.getId(), documentSpace.getName(), type.toString()));
				throw new IllegalArgumentException("Could not add privileges to user");
			}

			privilege.addDashboardUser(dashboardUser);
			privilegesToSave.add(privilege);
		});

		documentSpacePrivilegeRepository.saveAll(privilegesToSave);
	}

	@Override
	public void removePrivilegesFromDashboardUser(String dashboardUserEmail, DocumentSpace documentSpace,
			List<DocumentSpacePrivilegeType> privilegesToRemove) {
		DashboardUser dashboardUser = dashboardUserService.getDashboardUserByEmail(dashboardUserEmail);

		if (dashboardUser == null) {
			throw new RecordNotFoundException(
					String.format("Could not remove privileges to user with email: %s because they do not exist",
							dashboardUserEmail));
		}

		Map<DocumentSpacePrivilegeType, DocumentSpacePrivilege> documentSpacePrivileges = documentSpace.getPrivileges();
		List<DocumentSpacePrivilege> privilegesToSave = new ArrayList<>();

		privilegesToRemove.forEach(type -> {
			DocumentSpacePrivilege privilege = documentSpacePrivileges.get(type);

			if (privilege == null) {
				log.error(String.format(
						"Error removing Document Space privileges from user. Document Space: %s (%s) missing necessary privilege type: %s",
						documentSpace.getId(), documentSpace.getName(), type.toString()));
				throw new IllegalArgumentException("Could not remove privileges from user");
			}

			privilege.removeDashboardUser(dashboardUser);
			privilegesToSave.add(privilege);
		});

		documentSpacePrivilegeRepository.saveAll(privilegesToSave);
	}

	@Override
	public DashboardUser createDashboardUserWithPrivileges(String dashboardUserEmail, DocumentSpace documentSpace,
			List<DocumentSpacePrivilegeType> privilegesToAdd) {
		DashboardUser dashboardUser = dashboardUserService.createDashboardUserOrReturnExisting(dashboardUserEmail);
		
		addPrivilegesToDashboardUser(dashboardUser, documentSpace, privilegesToAdd);
		
		return dashboardUser;
	}

	@Override
	public DocumentSpacePrivilegeDto convertToDto(DocumentSpacePrivilege documentSpacePrivilege) {
		return MODEL_MAPPER.map(documentSpacePrivilege, DocumentSpacePrivilegeDto.class);
	}
	
	private DocumentSpacePrivilege buildDocumentSpacePrivilege(String privilegeName, DocumentSpacePrivilegeType type) {
		return DocumentSpacePrivilege.builder()
				.name(privilegeName)
				.type(type)
				.build();
	}
}

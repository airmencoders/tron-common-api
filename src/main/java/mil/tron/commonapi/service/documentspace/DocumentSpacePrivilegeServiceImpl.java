package mil.tron.commonapi.service.documentspace;

import lombok.extern.slf4j.Slf4j;
import mil.tron.commonapi.annotation.minio.IfMinioEnabledOnStagingIL4OrDevLocal;
import mil.tron.commonapi.dto.documentspace.DocumentSpacePrivilegeDto;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.documentspace.DocumentSpace;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceDashboardMemberPrivilegeRow;
import mil.tron.commonapi.entity.documentspace.DocumentSpacePrivilege;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.PrivilegeRepository;
import mil.tron.commonapi.repository.documentspace.DocumentSpacePrivilegeRepository;
import mil.tron.commonapi.service.DashboardUserService;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@IfMinioEnabledOnStagingIL4OrDevLocal
public class DocumentSpacePrivilegeServiceImpl implements DocumentSpacePrivilegeService {
	private static final ModelMapper MODEL_MAPPER = new DtoMapper();

	private final DocumentSpacePrivilegeRepository documentSpacePrivilegeRepository;
	private final DashboardUserService dashboardUserService;
	private final PrivilegeRepository privilegeRepository;

	public DocumentSpacePrivilegeServiceImpl(DocumentSpacePrivilegeRepository documentSpacePrivilegeRepository,
			DashboardUserService dashboardUserService, PrivilegeRepository privilegeRepository) {
		this.documentSpacePrivilegeRepository = documentSpacePrivilegeRepository;
		this.dashboardUserService = dashboardUserService;
		this.privilegeRepository = privilegeRepository;
	}
	
	@Override
	public void deleteAllPrivilegesBelongingToDocumentSpace(DocumentSpace documentSpace) {
		List<DocumentSpacePrivilege> privileges = new ArrayList<>(documentSpace.getPrivileges().values());
		List<DocumentSpacePrivilege> privilegesToDelete = new ArrayList<>();
		privileges.forEach(privilege -> {
			for (DashboardUser dashboardUser : new HashSet<>(privilege.getDashboardUsers())) {
				privilege.removeDashboardUser(dashboardUser);
			}
			
			for (AppClientUser appClientUser : new HashSet<>(privilege.getAppClientUsers())) {
				privilege.removeAppClientUser(appClientUser);
			}
			
			privilegesToDelete.add(privilege);
			documentSpace.removePrivilege(privilege);
		});
		
 		documentSpacePrivilegeRepository.deleteAll(privilegesToDelete);
	}

	@Override
	public void createAndSavePrivilegesForNewSpace(DocumentSpace documentSpace) {

		List<DocumentSpacePrivilege> privilegesToAdd = new ArrayList<>();
		
		DocumentSpacePrivilegeType[] documentSpacePrivilegeTypes = DocumentSpacePrivilegeType.values();
		
		for (int i = 0; i < documentSpacePrivilegeTypes.length; i++) {
			DocumentSpacePrivilegeType currentType = documentSpacePrivilegeTypes[i];
			
			DocumentSpacePrivilege privilege = buildDocumentSpacePrivilege(createPrivilegeName(documentSpace.getId(), currentType), currentType);
			
			privilegesToAdd.add(privilege);
			documentSpace.addPrivilege(privilege);
		}
		
		documentSpacePrivilegeRepository.saveAll(privilegesToAdd);
	}

	@Override
	public String createPrivilegeName(UUID documentSpaceId, DocumentSpacePrivilegeType privilegeType) {
		return String.format("DOCUMENT_SPACE_%s_%s", documentSpaceId.toString(), privilegeType);
	}
	
	@Override
	public void addPrivilegesToDashboardUser(DashboardUser dashboardUser, DocumentSpace documentSpace,
			List<DocumentSpacePrivilegeType> privilegesToAdd) throws IllegalArgumentException {

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
	public void removePrivilegesFromDashboardUser(String dashboardUserEmail, DocumentSpace documentSpace) {
		DashboardUser dashboardUser = dashboardUserService.getDashboardUserByEmail(dashboardUserEmail);

		List<DocumentSpacePrivilegeType> privilegeList = Arrays.asList(DocumentSpacePrivilegeType.values());

		if (dashboardUser == null) {
			throw new RecordNotFoundException(
					String.format("Could not remove privileges from user with email: %s because they do not exist", dashboardUserEmail));
		}

		Map<DocumentSpacePrivilegeType, DocumentSpacePrivilege> documentSpacePrivileges = documentSpace.getPrivileges();
		List<DocumentSpacePrivilege> privilegesToSave = new ArrayList<>();

		privilegeList.forEach(type -> {
			DocumentSpacePrivilege privilege = documentSpacePrivileges.get(type);
			if (privilege != null) {
				privilege.removeDashboardUser(dashboardUser);
				privilegesToSave.add(privilege);
			}
		});

		documentSpacePrivilegeRepository.saveAll(privilegesToSave);
	}

	@Override
	public DashboardUser createDashboardUserWithPrivileges(String dashboardUserEmail, DocumentSpace documentSpace,
			List<DocumentSpacePrivilegeType> privilegesToAdd) {
		DashboardUser dashboardUser = dashboardUserService.createDashboardUserOrReturnExisting(dashboardUserEmail);
		
		addPrivilegesToDashboardUser(dashboardUser, documentSpace, privilegesToAdd);
		
		Optional<Privilege> documentSpaceGlobalPrivilege = privilegeRepository.findByName(DocumentSpaceServiceImpl.DOCUMENT_SPACE_USER_PRIVILEGE);
		documentSpaceGlobalPrivilege.ifPresentOrElse(
				dashboardUser::addPrivilege,
				() -> log.error(String.format("Global Document Space Privilege (%s) is missing", DocumentSpaceServiceImpl.DOCUMENT_SPACE_USER_PRIVILEGE)));

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

	@Override
	public List<DocumentSpaceDashboardMemberPrivilegeRow> getAllDashboardMemberPrivilegeRowsForDocumentSpace(
			DocumentSpace documentSpace, Set<UUID> dashboardUserIdsToInclude) {
		return documentSpacePrivilegeRepository.findAllDashboardMemberPrivilegesBelongingToDocumentSpace(documentSpace.getId(), dashboardUserIdsToInclude);
	}
}

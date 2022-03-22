package mil.tron.commonapi.service.documentspace;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import mil.tron.commonapi.annotation.minio.IfMinioEnabledOnIL4OrDevLocal;
import mil.tron.commonapi.dto.appclient.AppClientSummaryDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceAppClientMemberRequestDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpaceAppClientResponseDto;
import mil.tron.commonapi.dto.documentspace.DocumentSpacePrivilegeDto;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.documentspace.DocumentSpace;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceDashboardMemberPrivilegeRow;
import mil.tron.commonapi.entity.documentspace.DocumentSpacePrivilege;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import mil.tron.commonapi.repository.documentspace.DocumentSpacePrivilegeRepository;
import mil.tron.commonapi.service.DashboardUserService;
import org.assertj.core.util.Lists;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@IfMinioEnabledOnIL4OrDevLocal
public class DocumentSpacePrivilegeServiceImpl implements DocumentSpacePrivilegeService {
	private static final ModelMapper MODEL_MAPPER = new DtoMapper();

	private final DocumentSpacePrivilegeRepository documentSpacePrivilegeRepository;
	private final DashboardUserService dashboardUserService;
	private final PrivilegeRepository privilegeRepository;
	private final AppClientUserRespository appClientUserRespository;

	public DocumentSpacePrivilegeServiceImpl(DocumentSpacePrivilegeRepository documentSpacePrivilegeRepository,
											 DashboardUserService dashboardUserService,
											 PrivilegeRepository privilegeRepository,
											 AppClientUserRespository appClientUserRespository) {

		this.documentSpacePrivilegeRepository = documentSpacePrivilegeRepository;
		this.dashboardUserService = dashboardUserService;
		this.privilegeRepository = privilegeRepository;
		this.appClientUserRespository = appClientUserRespository;
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

	// adds privilege(s) to user and any implicit ones that come with it (e.g. MEMBERSHIP gives WRITE and READ for free)
	@Override
	public void addPrivilegesToDashboardUser(DashboardUser dashboardUser, DocumentSpace documentSpace,
			List<DocumentSpacePrivilegeType> privilegesToAdd) throws IllegalArgumentException {

		Map<DocumentSpacePrivilegeType, DocumentSpacePrivilege> documentSpacePrivileges = documentSpace.getPrivileges();
		List<DocumentSpacePrivilege> privilegesToSave = new ArrayList<>();

		// remove, and then we add in
		removePrivilegesFromDashboardUser(dashboardUser, documentSpace);

		privilegesToAdd.forEach(type -> {

			// fallthrough case for making sure we inherit the subordinate privs given a higher one
			switch (type) {
				case MEMBERSHIP: //NOSONAR
					addSinglePrivilegeToUser(documentSpace,
							DocumentSpacePrivilegeType.MEMBERSHIP,
							documentSpacePrivileges.get(DocumentSpacePrivilegeType.MEMBERSHIP),
							dashboardUser,
							privilegesToSave);
				case WRITE: //NOSONAR
					addSinglePrivilegeToUser(documentSpace,
							DocumentSpacePrivilegeType.WRITE,
							documentSpacePrivileges.get(DocumentSpacePrivilegeType.WRITE),
							dashboardUser,
							privilegesToSave);

				// no matter what person gets read if adding them to a space..
				case READ: //NOSONAR
				default:
					addSinglePrivilegeToUser(documentSpace,
							DocumentSpacePrivilegeType.READ,
							documentSpacePrivileges.get(DocumentSpacePrivilegeType.READ),
							dashboardUser,
							privilegesToSave);
			}
		});

		documentSpacePrivilegeRepository.saveAll(privilegesToSave);
	}

	private void addSinglePrivilegeToUser(DocumentSpace documentSpace,
			 							  DocumentSpacePrivilegeType type,
			 							  @Nullable DocumentSpacePrivilege privilege,
										  DashboardUser dashboardUser,
										  List<DocumentSpacePrivilege> privilegesToSave) {
		if (privilege == null) {
			log.error(String.format(
					"Error adding Document Space privileges to user. Document Space: %s (%s) missing necessary privilege type: %s",
					documentSpace.getId(), documentSpace.getName(), type.toString()));
			throw new IllegalArgumentException("Could not add privileges to user");
		}

		privilege.addDashboardUser(dashboardUser);
		privilegesToSave.add(privilege);
	}

	@Override
	public void removePrivilegesFromDashboardUser(DashboardUser dashboardUser, DocumentSpace documentSpace) {

		List<DocumentSpacePrivilegeType> privilegeList = Arrays.asList(DocumentSpacePrivilegeType.values());

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

	/**
	 * Adds an app client user to a document space with given privileges
	 * @param documentSpace
	 * @param appClientId
	 * @param privilegesToAdd
	 */
	@Override
	public void addPrivilegesToAppClientUser(DocumentSpace documentSpace, UUID appClientId, List<DocumentSpacePrivilegeType> privilegesToAdd) {

		// validate the AppClient is real
		AppClientUser appClientUser = appClientUserRespository.findById(appClientId)
				.orElseThrow(() -> new RecordNotFoundException(String.format("App Client of id %s does not exist", appClientId)));


		Map<DocumentSpacePrivilegeType, DocumentSpacePrivilege> documentSpacePrivileges = documentSpace.getPrivileges();
		List<DocumentSpacePrivilege> privilegesToSave = new ArrayList<>();

		// remove, and then we add in
		removePrivilegesFromAppClientUser(documentSpace, appClientId);

		privilegesToAdd.forEach(type -> {

			// fallthrough case for making sure we inherit the subordinate privs given a higher one
			switch (type) {
				case MEMBERSHIP: //NOSONAR
					addSinglePrivilegeToAppClientUser(documentSpace,
							DocumentSpacePrivilegeType.MEMBERSHIP,
							documentSpacePrivileges.get(DocumentSpacePrivilegeType.MEMBERSHIP),
							appClientUser,
							privilegesToSave);
				case WRITE: //NOSONAR
					addSinglePrivilegeToAppClientUser(documentSpace,
							DocumentSpacePrivilegeType.WRITE,
							documentSpacePrivileges.get(DocumentSpacePrivilegeType.WRITE),
							appClientUser,
							privilegesToSave);

					// no matter what person gets read if adding them to a space..
				case READ: //NOSONAR
				default:
					addSinglePrivilegeToAppClientUser(documentSpace,
							DocumentSpacePrivilegeType.READ,
							documentSpacePrivileges.get(DocumentSpacePrivilegeType.READ),
							appClientUser,
							privilegesToSave);
			}
		});

		documentSpacePrivilegeRepository.saveAll(privilegesToSave);
	}

	private void addSinglePrivilegeToAppClientUser(DocumentSpace documentSpace,
										  DocumentSpacePrivilegeType type,
										  @Nullable DocumentSpacePrivilege privilege,
										  AppClientUser appClientUser,
										  List<DocumentSpacePrivilege> privilegesToSave) {
		if (privilege == null) {
			log.error(String.format(
					"Error adding Document Space privileges to app client. Document Space: %s (%s) missing necessary privilege type: %s",
					documentSpace.getId(), documentSpace.getName(), type));
			throw new IllegalArgumentException("Could not add privileges to app client");
		}

		privilege.addAppClientUser(appClientUser);
		privilegesToSave.add(privilege);
	}

	@Override
	public void removePrivilegesFromAppClientUser(DocumentSpace documentSpace, UUID appClientId) {

		// validate the AppClient is real
		AppClientUser appClientUser = appClientUserRespository.findById(appClientId)
				.orElseThrow(() -> new RecordNotFoundException(String.format("App Client of id %s does not exist", appClientId)));

		List<DocumentSpacePrivilegeType> privilegeList = Arrays.asList(DocumentSpacePrivilegeType.values());

		Map<DocumentSpacePrivilegeType, DocumentSpacePrivilege> documentSpacePrivileges = documentSpace.getPrivileges();
		List<DocumentSpacePrivilege> privilegesToSave = new ArrayList<>();

		privilegeList.forEach(type -> {
			DocumentSpacePrivilege privilege = documentSpacePrivileges.get(type);
			if (privilege != null) {
				privilege.removeAppClientUser(appClientUser);
				privilegesToSave.add(privilege);
			}
		});

		documentSpacePrivilegeRepository.saveAll(privilegesToSave);
	}

	@Override
	public List<DocumentSpaceAppClientResponseDto> getAppClientsForDocumentSpace(DocumentSpace documentSpace) {
		Map<DocumentSpacePrivilegeType, DocumentSpacePrivilege> privs = documentSpace.getPrivileges();
		Map<UUID, DocumentSpaceAppClientResponseDto> clients = new HashMap<>();

		privs.forEach((key, value) -> {
			value.getAppClientUsers().forEach(client -> {
				if (clients.containsKey(client.getId())) {
					List<DocumentSpacePrivilegeType> clientPrivs = clients.get(client.getId()).getPrivileges();
					clientPrivs.add(key);
					clients.put(client.getId(), DocumentSpaceAppClientResponseDto.builder()
							.appClientId(client.getId())
							.appClientName(client.getName())
							.privileges(clientPrivs)
							.build());
				} else {
					clients.put(client.getId(), DocumentSpaceAppClientResponseDto.builder()
							.appClientId(client.getId())
							.appClientName(client.getName())
							.privileges(Lists.newArrayList(key))
							.build());
				}
			});
		});

		return new ArrayList<>(clients.values());
	}

	/**
	 * Provides a list of app clients that are available for assignment (not currently assigned) to given document space
	 * @param documentSpace
	 * @return
	 */
	@Override
	public List<AppClientSummaryDto> getAppClientsForAssignmentToDocumentSpace(DocumentSpace documentSpace) {
		List<AppClientUser> appClients = appClientUserRespository.findAll();
		List<UUID> assignedAppClientIds = this.getAppClientsForDocumentSpace(documentSpace).stream()
				.map(DocumentSpaceAppClientResponseDto::getAppClientId).collect(Collectors.toList());

		// diff the lists to produce a unique set app clients that are not current assigned to this doc space
		return appClients.stream()
				.filter(item -> !assignedAppClientIds.contains(item.getId()))
				.map(item -> AppClientSummaryDto.builder()
						.id(item.getId())
						.name(item.getName())
						.build())
				.collect(Collectors.toList());
	}
}

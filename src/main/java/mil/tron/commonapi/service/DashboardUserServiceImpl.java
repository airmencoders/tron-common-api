package mil.tron.commonapi.service;

import mil.tron.commonapi.dto.DashboardUserDto;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import mil.tron.commonapi.service.utility.DashboardUserUniqueChecksService;
import org.modelmapper.Conditions;
import org.modelmapper.Converter;
import org.modelmapper.spi.MappingContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class DashboardUserServiceImpl implements DashboardUserService {
    private DashboardUserRepository dashboardUserRepository;
    private DashboardUserUniqueChecksService userChecksService;
    private static final String RESOURCE_NOT_FOUND_MSG = "User with the ID: %s does not exist.";
    private final DtoMapper modelMapper;
    private AppSourceService appSourceService;
    private AppClientUserService appClientUserService;

    private PrivilegeRepository privRepo;

    public DashboardUserServiceImpl(DashboardUserRepository dashboardUserRepository,
                                    DashboardUserUniqueChecksService dashboardUserUniqueChecksService,
                                    PrivilegeRepository privilegeRepository,
                                    @Lazy AppSourceService appSourceService,
                                    @Lazy AppClientUserService appClientUserService) {
        this.dashboardUserRepository = dashboardUserRepository;
        this.userChecksService = dashboardUserUniqueChecksService;
        this.appSourceService = appSourceService;
        this.appClientUserService = appClientUserService;
        this.privRepo = privilegeRepository;
        this.modelMapper = new DtoMapper();
        this.modelMapper.getConfiguration().setPropertyCondition(Conditions.isNotNull());

        Converter<List<Privilege>, Set<Privilege>> convertPrivilegesToSet =
                ((MappingContext<List<Privilege>, Set<Privilege>> context) -> new HashSet<>(context.getSource()));

        Converter<Set<Privilege>, List<Privilege>> convertPrivilegesToArr =
                ((MappingContext<Set<Privilege>, List<Privilege>> context) -> new ArrayList<>(context.getSource()));

        this.modelMapper.addConverter(convertPrivilegesToSet);
        this.modelMapper.addConverter(convertPrivilegesToArr);
    }

    @Override
    public DashboardUserDto createDashboardUserDto(DashboardUserDto dashboardUserDto) {
        DashboardUser dashboardUser = convertToEntity(dashboardUserDto);
        if (dashboardUser.getId() == null) {
            // we have to generate an ID manually since we're not using normal
            dashboardUser.setId(UUID.randomUUID());
        }

        if (!userChecksService.userEmailIsUnique(dashboardUser)) {
            throw new ResourceAlreadyExistsException(String.format("dashboardUser with the email: %s already exists", dashboardUser.getEmail()));
        }

        Privilege dashBoardUserPriv = privRepo
                .findByName("DASHBOARD_USER")
                .orElseThrow(() -> new RecordNotFoundException("Cannot find the DASHBOARD_USER privilege"));

        // should have at least the DASHBOARD_USER priv
        dashboardUser.getPrivileges().add(dashBoardUserPriv);

        // the record with this 'id' shouldn't already exist...
        if (!dashboardUserRepository.existsById(dashboardUser.getId())) {
            return convertToDto(dashboardUserRepository.save(dashboardUser));
        }

        throw new ResourceAlreadyExistsException("Dashboard User with the id: " + dashboardUser.getId() + " already exists.");
    }

    @Override
    public Iterable<DashboardUserDto> getAllDashboardUsersDto() {
        return StreamSupport.stream(dashboardUserRepository.findAll().spliterator(), false).map(this::convertToDto).collect(Collectors.toList());
    }

    @Override
    public DashboardUserDto getDashboardUserDto(UUID id) {
        return convertToDto(dashboardUserRepository.findById(id).orElseThrow(() -> new RecordNotFoundException(String.format(RESOURCE_NOT_FOUND_MSG, id))));
    }

    @Override
    public DashboardUserDto updateDashboardUserDto(UUID id, DashboardUserDto dashboardUserDto) {
        DashboardUser dashboardUser = convertToEntity(dashboardUserDto);
        // Ensure the id given matches the id of the object given
        if (!id.equals(dashboardUser.getId()))
            throw new InvalidRecordUpdateRequest(String.format("ID: %s does not match the resource ID: %s", id, dashboardUser.getId()));

        Optional<DashboardUser> dbDashboardUser = dashboardUserRepository.findById(id);

        if (dbDashboardUser.isEmpty())
            throw new RecordNotFoundException("Dashboard User resource with the ID: " + id + " does not exist.");

        if (!userChecksService.userEmailIsUnique(dashboardUser)) {
            throw new InvalidRecordUpdateRequest(String.format("Email: %s is already in use.", dashboardUser.getEmail()));
        }

        if (dashboardUser.getPrivileges().stream().count() == 0) {
            throw new InvalidRecordUpdateRequest("A privilege must be set");
        }

        return convertToDto(dashboardUserRepository.save(dashboardUser));
    }

    @Override
    public void deleteDashboardUser(UUID id) {
        DashboardUser user = dashboardUserRepository.findById(id).orElseThrow(() ->
                new RecordNotFoundException("Record with ID: " + id.toString() + " not found."));

        appSourceService.deleteAdminFromAllAppSources(user);
        appClientUserService.deleteDeveloperFromAllAppClient(user);
        dashboardUserRepository.delete(user);
    }

    @Override
    public DashboardUserDto convertToDto(DashboardUser user) {
        return modelMapper.map(user, DashboardUserDto.class);
    }

    @Override
    public DashboardUser convertToEntity(DashboardUserDto dto) {
        return modelMapper.map(dto, DashboardUser.class);
    }

	@Override
	public DashboardUserDto getSelf(String email) {
		return convertToDto(dashboardUserRepository.findByEmailIgnoreCase(email).orElseThrow(() -> new UsernameNotFoundException("Dashboard User: " + email + " not found.")));
	}
}

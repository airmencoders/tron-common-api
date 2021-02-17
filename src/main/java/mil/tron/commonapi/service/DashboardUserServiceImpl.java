package mil.tron.commonapi.service;

import io.swagger.v3.oas.annotations.Operation;
import lombok.val;
import mil.tron.commonapi.dto.DashboardUserDto;
import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import mil.tron.commonapi.service.utility.DashboardUserUniqueChecksService;
import org.modelmapper.Conditions;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class DashboardUserServiceImpl implements DashboardUserService {
    private static final DtoMapper MODEL_MAPPER = new DtoMapper();
    private DashboardUserRepository dashboardUserRepository;
    private DashboardUserUniqueChecksService userChecksService;
    private PrivilegeRepository privilegeRepository;
    private static final String RESOURCE_NOT_FOUND_MSG = "User with the ID: %s does not exist.";
    private final DtoMapper modelMapper;

    public DashboardUserServiceImpl(DashboardUserRepository dashboardUserRepository,
                                    DashboardUserUniqueChecksService dashboardUserUniqueChecksService,
                                    PrivilegeRepository privilegeRepository) {
        this.dashboardUserRepository = dashboardUserRepository;
        this.userChecksService = dashboardUserUniqueChecksService;
        this.privilegeRepository = privilegeRepository;
        this.modelMapper = new DtoMapper();
        modelMapper.getConfiguration().setPropertyCondition(Conditions.isNotNull());
    }

    @Override
    public DashboardUserDto createDashboardUser(DashboardUserDto dashboardUserDto) {
        DashboardUser dashboardUser = convertToEntity(dashboardUserDto);
        if (dashboardUser.getId() == null) {
            // we have to generate an ID manually since we're not using normal
            dashboardUser.setId(UUID.randomUUID());
        }

        if (!userChecksService.UserEmailIsUnique(dashboardUser))
            throw new ResourceAlreadyExistsException(String.format("dashboardUser with the email: %s already exists", dashboardUser.getEmail()));

        // the record with this 'id' shouldn't already exist...
        // For now any newly added user is granted DASHBOARD_USER privileges only
        if (!dashboardUserRepository.existsById(dashboardUser.getId())) {
            Set<Privilege> defaultPrivilege = this.privilegeRepository.findByName("DASHBOARD_USER").map(Collections::singleton).orElse(Collections.emptySet());
            if (defaultPrivilege != null && defaultPrivilege.stream().count() == 1) {
                dashboardUser.setPrivileges(defaultPrivilege);
            }
            return convertToDto(dashboardUserRepository.save(dashboardUser));
        }

        throw new ResourceAlreadyExistsException("Dashboard User with the id: " + dashboardUser.getId() + " already exists.");
    }

    @Override
    @Operation(summary = "Retrieves all dashboard users", description = "Retrieves all dashboard users")
    public Iterable<DashboardUserDto> getAllDashboardUsers() {
        return StreamSupport.stream(dashboardUserRepository.findAll().spliterator(), false).map(this::convertToDto).collect(Collectors.toList());
    }

    @Override
    public DashboardUserDto getDashboardUserDto(UUID id) {
        return convertToDto(dashboardUserRepository.findById(id).orElseThrow(() -> new RecordNotFoundException(String.format(RESOURCE_NOT_FOUND_MSG, id))));
    }

    @Override
    public DashboardUserDto convertToDto(DashboardUser user) {
        return MODEL_MAPPER.map(user, DashboardUserDto.class);
    }

    @Override
    public DashboardUser convertToEntity(DashboardUserDto dto) {
        DashboardUser entity = modelMapper.map(dto, DashboardUser.class);
        Set<Privilege> defaultPrivilege = this.privilegeRepository.findByName("DASHBOARD_USER").map(Collections::singleton).orElse(Collections.emptySet());
        if (defaultPrivilege != null && defaultPrivilege.stream().count() == 1) {
            entity.setPrivileges(defaultPrivilege);
        }
        return entity;
    }
}

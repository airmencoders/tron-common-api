package mil.tron.commonapi.service;

import io.swagger.v3.oas.annotations.Operation;
import lombok.val;
import mil.tron.commonapi.dto.DashboardUserDto;
import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.exception.InvalidFieldValueException;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.DashboardUserRepository;
import mil.tron.commonapi.repository.PrivilegeRepository;
import mil.tron.commonapi.service.utility.DashboardUserUniqueChecksService;
import org.modelmapper.Conditions;
import org.modelmapper.Converter;
import org.modelmapper.spi.MappingContext;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class DashboardUserServiceImpl implements DashboardUserService {
//    private static final DtoMapper MODEL_MAPPER = new DtoMapper();
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

        if (!userChecksService.UserEmailIsUnique(dashboardUser)) {
            throw new ResourceAlreadyExistsException(String.format("dashboardUser with the email: %s already exists", dashboardUser.getEmail()));
        }

        if (dashboardUser.getPrivileges().stream().count() == 0) {
            throw new InvalidRecordUpdateRequest(String.format("A privilege must be set"));
        }

        // the record with this 'id' shouldn't already exist...
        if (!dashboardUserRepository.existsById(dashboardUser.getId())) {
            return convertToDto(dashboardUserRepository.save(dashboardUser));
        }

        throw new ResourceAlreadyExistsException("Dashboard User with the id: " + dashboardUser.getId() + " already exists.");
    }

    @Override
    @Operation(summary = "Retrieves all dashboard users", description = "Retrieves all dashboard users")
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

        if (!userChecksService.UserEmailIsUnique(dashboardUser)) {
            throw new InvalidRecordUpdateRequest(String.format("Email: %s is already in use.", dashboardUser.getEmail()));
        }

        if (dashboardUser.getPrivileges().stream().count() == 0) {
            throw new InvalidRecordUpdateRequest(String.format("A privilege must be set"));
        }

        return convertToDto(dashboardUserRepository.save(dashboardUser));
    }

    @Override
    public void deleteDashboardUser(UUID id) {
        if (dashboardUserRepository.existsById(id)) {
            dashboardUserRepository.deleteById(id);
        }
        else {
            throw new RecordNotFoundException("Record with ID: " + id.toString() + " not found.");
        }
    }

    @Override
    public DashboardUserDto convertToDto(DashboardUser user) {
        return modelMapper.map(user, DashboardUserDto.class);
    }

    @Override
    public DashboardUser convertToEntity(DashboardUserDto dto) {
        DashboardUser entity = modelMapper.map(dto, DashboardUser.class);
        return entity;
    }
}

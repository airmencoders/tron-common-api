package mil.tron.commonapi.service;

import mil.tron.commonapi.dto.SquadronDto;
import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.entity.Airman;
import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.Squadron;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.SquadronRepository;
import mil.tron.commonapi.service.utility.OrganizationUniqueChecksService;
import org.modelmapper.AbstractConverter;
import org.modelmapper.Conditions;
import org.modelmapper.Converter;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class SquadronServiceImpl implements SquadronService {

    private final SquadronRepository squadronRepo;
    private final PersonService personService;
    private final OrganizationService orgService;
    private final OrganizationUniqueChecksService orgChecksService;
    private final DtoMapper modelMapper;
    private static final String RESOURCE_NOT_FOUND_MSG = "Squadron Resource with the ID: %s does not exist.";

    public SquadronServiceImpl(
            SquadronRepository squadronRepo,
            PersonService personService,
            OrganizationService orgService,
            OrganizationUniqueChecksService orgChecksService) {

        this.squadronRepo = squadronRepo;
        this.orgService = orgService;
        this.personService = personService;
        this.orgChecksService = orgChecksService;

        // allows us to not throw when source property is null, notably the objectId
        this.modelMapper = new DtoMapper();
    }

    /**
     * Finds a squadron by UUID and returns it as a full Squadron object
     * @param id UUID to look up
     * @return The Squadron entity that matches id
     */
    @Override
    public Squadron findSquadron(UUID id) {
        return squadronRepo.findById(id).orElseThrow(() -> new RecordNotFoundException("Squadron with ID: " + id.toString() + " does not exist."));
    }

    /**
     * Removes a member(s) by UUID from the referenced Squadron
     * @param organizationId UUID of the squadron
     * @param personIds List of Member UUIDs to remove from Squadron
     * @return The modified Squadron object
     */
    @Override
    public Squadron removeMember(UUID organizationId, List<UUID> personIds) {
        Organization org = orgService.removeMember(organizationId, personIds);

        if (!(org instanceof Squadron)) {
            throw new InvalidRecordUpdateRequest("Unable to modify squadron members");
        }

        Squadron squadron = (Squadron) org;
        return squadronRepo.save(squadron);
    }

    /**
     * Adds a member(s) by UUID to the referenced Squadron
     * @param organizationId UUID of the Squadron
     * @param personIds List of Member UUIDs to add to Squadron
     * @return The modified Squadron object
     */
    @Override
    public Squadron addMember(UUID organizationId, List<UUID> personIds) {
        Organization org = orgService.addMember(organizationId, personIds);

        if (!(org instanceof Squadron)) {
            throw new InvalidRecordUpdateRequest("Unable to modify squadron members");
        }

        Squadron squadron = (Squadron) org;
        return squadronRepo.save(squadron);
    }

    /**
     * Modifies a Squadron object's non-collection type properties
     * @param squadronId The UUID of the squadron to modify
     * @param attributes Map of the attributes to apply/change (String key-value pairs)
     * @return The modified Squadron object
     */
    @Override
    public Squadron modify(UUID squadronId, Map<String, String> attributes) {
        // pass the squadron thru to the parent class to change org-only-level attributes if needed
        Organization org = orgService.modify(squadronId, attributes);

        if (!(org instanceof Squadron)) {
            throw new InvalidRecordUpdateRequest("Unable to modify squadron attributes");
        }

        Squadron squadron = (Squadron) org;

        attributes.forEach((k, v) -> {
            Field field = ReflectionUtils.findField(Squadron.class, k);
            try {
                if (field != null) {
                    String setterName = "set" + field.getName().substring(0, 1).toUpperCase() + field.getName().substring(1);
                    Method setterMethod = squadron.getClass().getMethod(setterName, field.getType());
                    if (v == null) {
                        ReflectionUtils.invokeMethod(setterMethod, squadron, (Object) null);
                    } else if (field.getType().equals(Airman.class) || field.getType().equals(Person.class)) {
                        Person person = personService.getPerson(UUID.fromString(v));
                        ReflectionUtils.invokeMethod(setterMethod, squadron, person);
                    } else if (field.getType().equals(Squadron.class) || field.getType().equals(Organization.class)) {
                        Organization sqdn = orgService.findOrganization(UUID.fromString(v));
                        ReflectionUtils.invokeMethod(setterMethod, squadron, sqdn);
                    } else if (field.getType().equals(String.class)) {
                        ReflectionUtils.invokeMethod(setterMethod, squadron, v);
                    }
                    else {
                        throw new InvalidRecordUpdateRequest("Field: " + field.getName() + " is not of recognized type");
                    }
                }
            }
            catch (NoSuchMethodException e) {
                throw new InvalidRecordUpdateRequest("Provided field: " + field.getName() + " is not settable");
            }
        });

        // commit
        return squadronRepo.save(squadron);
    }

    /**
     * Creates a new Squadron object persisted in the database
     * @param sqd The Squadron DTO with the new Squadron's information (UUID is optional)
     * @return The new Squadron converted to DTO form
     */
    @Override
    public SquadronDto createSquadron(SquadronDto sqd) {

        Squadron squadron = this.convertToEntity(sqd);

        if (!orgChecksService.orgNameIsUnique(squadron)) {
            throw new ResourceAlreadyExistsException(String.format("Squadron with the Name: %s already exists.", squadron.getName()));
        }

        // the record with this 'id' shouldn't already exist...
        if (!squadronRepo.existsById(squadron.getId())) {
            return this.convertToDto(squadronRepo.save(squadron));
        }

        throw new ResourceAlreadyExistsException("Squadron with ID: " + squadron.getId().toString() + " already exists.");
    }

    /**
     * Updates an existing squadron
     * @param id UUID of the Squadron to update
     * @param sqd The DTO of the Squadron to update with
     * @return The modified squadron in DTO form
     */
    @Override
    public SquadronDto updateSquadron(UUID id, SquadronDto sqd) {

        Squadron squadron = this.convertToEntity(sqd);

        if (!squadronRepo.existsById(id)) {
            throw new RecordNotFoundException(String.format(RESOURCE_NOT_FOUND_MSG, "squadron", id.toString()));
        }

        // the squadrons object's id better match the id given,
        //  otherwise hibernate will save under whatever id's inside the object
        if (!squadron.getId().equals(id)) {
            throw new InvalidRecordUpdateRequest(
                    "Provided squadron UUID mismatched UUID " + id.toString() + " in squadron object");
        }

        if (!orgChecksService.orgNameIsUnique(squadron)) {
            throw new InvalidRecordUpdateRequest(String.format("Squadron Name: %s is already in use.", squadron.getName()));
        }

        return this.convertToDto(squadronRepo.save(squadron));
    }

    /**
     * Removes a squadron from the database by UUID, if it exists
     * @param id The UUID of the Squadron to delete
     */
    @Override
    public void removeSquadron(UUID id) {
        if (squadronRepo.existsById(id)) {
            squadronRepo.deleteById(id);
        }
        else {
            throw new RecordNotFoundException("Squadron record with provided UUID " + id.toString() + " does not exist");
        }
    }

    /**
     * Gets a list of all Squadrons, all converted to their DTO forms
     * @return List of SquadronDTOs
     */
    @Override
    public Iterable<SquadronDto> getAllSquadrons() {
        return StreamSupport
                .stream(squadronRepo.findAll().spliterator(), false)
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Gets a single Squadron (DTO form) by UUID
     * @param id UUID of the squadron to fetch
     * @return The Squadron DTO matching id
     */
    @Override
    public SquadronDto getSquadron(UUID id) {
        return this.convertToDto(this.findSquadron(id));
    }

    /**
     * Removes one or more airmen from the given squadron.
     *
     * @param squadronId UUID of the squadron from from which to remove members from
     * @param airmanIds UUID(s) of the airmen to remove
     * @return modified and persisted Squadron
     */
    @Override
    public SquadronDto removeSquadronMember(UUID squadronId, List<UUID> airmanIds) {
        return this.convertToDto(this.removeMember(squadronId, airmanIds));
    }

    /**
     * Adds an array of 1 or more airmen to the given squadron.
     *
     * @param squadronId UUID of the squadron to add members to
     * @param airmanIds UUID(s) of the airmen to add
     * @return modified and persisted Squadron
     */
    @Override
    public SquadronDto addSquadronMember(UUID squadronId, List<UUID> airmanIds) {
        return this.convertToDto(this.addMember(squadronId, airmanIds));
    }

    /**
     * Modifies a squadrons attributes except members.  This method calls the parent class to set any
     * provided fields at the org-level as well.
     *
     * @param squadronId UUID of the squadron
     * @param attributes Hashmap of key/value pairs (strings) presenting the field(s) to change
     * @return modified and persisted Squadron
     */
    @Override
    public SquadronDto modifySquadronAttributes(UUID squadronId, Map<String, String> attributes) {
        return this.convertToDto(this.modify(squadronId, attributes));
    }

    /**
     * Creates new squadrons input as a list (in DTO form)
     * @param newSquadrons List of new SquadronDTO objects to create
     * @return Same as the input list if all squadrons were successfully created
     */
    @Override
    public List<SquadronDto> bulkAddSquadrons(List<SquadronDto> newSquadrons) {
        List<SquadronDto> addedSquadrons = new ArrayList<>();
        for (SquadronDto s : newSquadrons) {
            addedSquadrons.add(this.createSquadron(s));
        }

        return addedSquadrons;
    }

    /**
     * Converts a squadron entity with all its nested entities into a more terse
     * structure with only UUID representations for types of Org/Person
     *
     * Using model mapper here allows mapping autonomously of most fields except for the
     * ones of custom type - where we have to explicitly grab out the UUID field since model
     * mapper has no clue
     * @param squadron The squadron object to convert to a DTO for the controller class to use
     * @return object of type SquadronTerseDto
     */
    @Override
    public SquadronDto convertToDto(Squadron squadron) {

        // converter to change any airman types to Object to ModelMapper can work correctly
        Converter<Airman, Object> personDemapper = new AbstractConverter<Airman, Object>() {
            @Override
            protected Object convert(Airman man) {
                return (Object) man;
            }
        };
        modelMapper.addConverter(personDemapper);
        modelMapper.getConfiguration().setPropertyCondition(Conditions.isNotNull());
        return modelMapper.map(squadron, SquadronDto.class);
    }

    /**
     * Converts a Squardron DTO back into its entity (if its found to exist).
     * @param squadronDto The DTO to convert into its entity type
     * @return The Squadron entity ibject
     */
    @Override
    public Squadron convertToEntity(SquadronDto squadronDto) {

        // use person service here for lookup since it covers all Person types
        Converter<UUID, Person> personDemapper = new AbstractConverter<UUID, Person>() {
            @Override
            protected Person convert(UUID uuid) {
                return personService.getPerson(uuid);
            }
        };

        // use org service here since it covers all Organizational Types
        Converter<UUID, Organization> orgDemapper = new AbstractConverter<UUID, Organization>() {
            @Override
            protected Organization convert(UUID uuid) {
                return orgService.findOrganization(uuid);
            }
        };
        modelMapper.getConfiguration().setPropertyCondition(Conditions.isNotNull());
        modelMapper.addConverter(personDemapper);
        modelMapper.addConverter(orgDemapper);
        return modelMapper.map(squadronDto, Squadron.class);
    }
}

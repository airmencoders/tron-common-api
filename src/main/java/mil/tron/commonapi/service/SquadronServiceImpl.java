package mil.tron.commonapi.service;

import mil.tron.commonapi.entity.Airman;
import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.AirmanRepository;
import mil.tron.commonapi.repository.SquadronRepository;
import mil.tron.commonapi.entity.Squadron;
import mil.tron.commonapi.service.utility.OrganizationUniqueChecksService;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SquadronServiceImpl implements SquadronService {

    private final SquadronRepository squadronRepo;
    private final AirmanRepository airmanRepo;
    private final OrganizationService orgService;
    private final OrganizationUniqueChecksService orgChecksService;
    private static final String RESOURCE_NOT_FOUND_MSG = "Squadron Resource with the ID: %s does not exist.";

    public SquadronServiceImpl(
            SquadronRepository squadronRepo,
            AirmanRepository airmanRepo,
            OrganizationService orgService,
            OrganizationUniqueChecksService orgChecksService) {

        this.squadronRepo = squadronRepo;
        this.airmanRepo = airmanRepo;
        this.orgService = orgService;
        this.orgChecksService = orgChecksService;
    }

    @Override
    public Squadron createSquadron(Squadron squadron) {
        if (squadron.getId() == null) {
            // we have to generate an ID manually since we're not using normal
            //  serial ID but rather an UUID for Person entity...
            squadron.setId(UUID.randomUUID());
        }

        if (!orgChecksService.orgNameIsUnique(squadron)) {
            throw new ResourceAlreadyExistsException(String.format("Squadron with the Name: %s already exists.", squadron.getName()));
        }

        // the record with this 'id' shouldn't already exist...
        if (!squadronRepo.existsById(squadron.getId())) {
            return squadronRepo.save(squadron);
        }

        throw new ResourceAlreadyExistsException("Squadron with ID: " + squadron.getId().toString() + " already exists.");
    }

    @Override
    public Squadron updateSquadron(UUID id, Squadron squadron) {

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

        return squadronRepo.save(squadron);
    }

    @Override
    public void removeSquadron(UUID id) {
        if (squadronRepo.existsById(id)) {
            squadronRepo.deleteById(id);
        }
        else {
            throw new RecordNotFoundException("Squadron record with provided UUID " + id.toString() + " does not exist");
        }
    }

    @Override
    public Iterable<Squadron> getAllSquadrons() {
        return squadronRepo.findAll();
    }

    @Override
    public Squadron getSquadron(UUID id) {
        return squadronRepo.findById(id).orElseThrow(() -> new RecordNotFoundException("Squadron with ID: " + id.toString() + " does not exist."));
    }

    /**
     * Removes one or more airmen from the given squadron.
     *
     * @param squadronId UUID of the squadron from from which to remove members from
     * @param airmanIds UUID(s) of the airmen to remove
     * @return modified and persisted Squadron
     */
    @Override
    public Squadron removeSquadronMember(UUID squadronId, List<UUID> airmanIds) {

        Organization org = orgService.removeOrganizationMember(squadronId, airmanIds);

        if (!(org instanceof Squadron)) {
            throw new InvalidRecordUpdateRequest("Unable to modify squadron members");
        }

        Squadron squadron = (Squadron) org;
        return squadronRepo.save(squadron);
    }

    /**
     * Adds an array of 1 or more airmen to the given squadron.
     *
     * @param squadronId UUID of the squadron to add members to
     * @param airmanIds UUID(s) of the airmen to add
     * @return modified and persisted Squadron
     */
    @Override
    public Squadron addSquadronMember(UUID squadronId, List<UUID> airmanIds) {

        Organization org = orgService.addOrganizationMember(squadronId, airmanIds);

        if (!(org instanceof Squadron)) {
            throw new InvalidRecordUpdateRequest("Unable to modify squadron members");
        }

        Squadron squadron = (Squadron) org;
        return squadronRepo.save(squadron);
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
    public Squadron modifySquadronAttributes(UUID squadronId, Map<String, String> attributes) {

        // pass the squadron thru to the parent class to change org-only-level attributes if needed
        Organization org = orgService.modifyAttributes(squadronId, attributes);

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
                    if (k.equals("id")) {
                        throw new InvalidRecordUpdateRequest("Cannot set/modify this record ID field");
                    } else if (v == null) {
                        ReflectionUtils.invokeMethod(setterMethod, squadron, (Object) null);
                    } else if (field.getType().equals(Airman.class) || field.getType().equals(Person.class)) {
                        Airman airman = airmanRepo.findById(UUID.fromString(v))
                                .orElseThrow(() -> new InvalidRecordUpdateRequest(String.format(RESOURCE_NOT_FOUND_MSG, "airman")));
                        ReflectionUtils.invokeMethod(setterMethod, squadron, airman);
                    } else if (field.getType().equals(Squadron.class)) {
                        Squadron sqdn = squadronRepo.findById(UUID.fromString(v)).orElseThrow(
                                () -> new InvalidRecordUpdateRequest(String.format(RESOURCE_NOT_FOUND_MSG, "squadron")));
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

    @Override
    public List<Squadron> bulkAddSquadrons(List<Squadron> newSquadrons) {
        List<Squadron> addedSquadrons = new ArrayList<>();
        for (Squadron s : newSquadrons) {
            addedSquadrons.add(this.createSquadron(s));
        }

        return addedSquadrons;
    }
}

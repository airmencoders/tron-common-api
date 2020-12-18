package mil.tron.commonapi.service;

import mil.tron.commonapi.entity.Airman;
import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.AirmanRepository;
import mil.tron.commonapi.repository.SquadronRepository;
import mil.tron.commonapi.entity.Squadron;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SquadronServiceImpl implements SquadronService {

    private final SquadronRepository squadronRepo;
    private final AirmanRepository airmanRepo;
    private final OrganizationService orgService;

    public SquadronServiceImpl(SquadronRepository squadronRepo, AirmanRepository airmanRepo, OrganizationService orgService) {
        this.squadronRepo = squadronRepo;
        this.airmanRepo = airmanRepo;
        this.orgService = orgService;
    }

    @Override
    public Squadron createSquadron(Squadron squadron) {
        if (squadron.getId() == null) {
            // we have to generate an ID manually since we're not using normal
            //  serial ID but rather an UUID for Person entity...
            squadron.setId(UUID.randomUUID());
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
            throw new RecordNotFoundException("Provided squadron UUID does not match any existing records");
        }

        // the squadrons object's id better match the id given,
        //  otherwise hibernate will save under whatever id's inside the object
        if (!squadron.getId().equals(id)) {
            throw new InvalidRecordUpdateRequest(
                    "Provided squadron UUID mismatched UUID in squadron object");
        }

        return squadronRepo.save(squadron);
    }

    @Override
    public void removeSquadron(UUID id) {
        if (squadronRepo.existsById(id)) {
            squadronRepo.deleteById(id);
        }
        else {
            throw new RecordNotFoundException("Squadron record with provided UUID does not exist");
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

        // change just squadron specific things
        // these are broken out to avoid SonarQube 'complexity' violations
        setDirector(squadron, attributes);
        setChief(squadron, attributes);
        setBaseName(squadron, attributes);
        setMajorCommand(squadron, attributes);

        // commit
        return squadronRepo.save(squadron);

    }

    private void setDirector(Squadron squadron, Map<String, String> attributes) {
        // update director if present
        final String DIRECTOR = "operationsDirector";
        if (attributes.containsKey(DIRECTOR)) {
            if (attributes.get(DIRECTOR) == null) {
                squadron.setOperationsDirector(null);
            }
            else {
                Airman airman = airmanRepo.findById(UUID.fromString(attributes.get(DIRECTOR)))
                        .orElseThrow(() -> new InvalidRecordUpdateRequest("Provided director UUID does not match any existing records"));

                squadron.setOperationsDirector(airman);
            }
        }
    }

    private void setChief(Squadron squadron, Map<String, String> attributes) {
        // update chief if present
        final String CHIEF = "chief";
        if (attributes.containsKey(CHIEF)) {
            if (attributes.get(CHIEF) == null) {
                squadron.setChief(null);
            }
            else {
                Airman airman = airmanRepo.findById(UUID.fromString(attributes.get(CHIEF)))
                        .orElseThrow(() -> new InvalidRecordUpdateRequest("Provided chief UUID does not match any existing records"));

                squadron.setChief(airman);
            }
        }
    }

    private void setBaseName(Squadron squadron, Map<String, String> attributes) {
        // update base name if present
        final String BASE_NAME = "baseName";
        if (attributes.containsKey(BASE_NAME)) {
            if (attributes.get(BASE_NAME) == null) {
                squadron.setBaseName(null);
            }
            else {
                squadron.setBaseName(attributes.get(BASE_NAME));
            }
        }
    }

    private void setMajorCommand(Squadron squadron, Map<String, String> attributes) {
        // update major command if present
        final String MAJ_COM = "majorCommand";
        if (attributes.containsKey(MAJ_COM)) {
            if (attributes.get(MAJ_COM) == null) {
                squadron.setMajorCommand(null);
            }
            else {
                squadron.setMajorCommand(attributes.get(MAJ_COM));
            }
        }
    }
}

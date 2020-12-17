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
        setLeader(squadron, attributes);
        setDirector(squadron, attributes);
        setChief(squadron, attributes);
        setBaseName(squadron, attributes);
        setMajorCommand(squadron, attributes);

        // commit
        return squadronRepo.save(squadron);

    }

    private void setLeader(Squadron squadron, Map<String, String> attributes) {
        if (attributes.get("leader") == null) {
            squadron.setLeader(null);
        }
        else {
            Airman airman = airmanRepo.findById(UUID.fromString(attributes.get("leader")))
                    .orElseThrow(() -> new RecordNotFoundException("Provided leader UUID does not match any existing records"));

            squadron.setLeader(airman);
        }
    }

    private void setDirector(Squadron squadron, Map<String, String> attributes) {
        // update director if present
        if (attributes.containsKey("operationsDirector")) {
            if (attributes.get("operationsDirector") == null) {
                squadron.setOperationsDirector(null);
            }
            else {
                Airman airman = airmanRepo.findById(UUID.fromString(attributes.get("operationsDirector")))
                        .orElseThrow(() -> new RecordNotFoundException("Provided director UUID does not match any existing records"));

                squadron.setOperationsDirector(airman);
            }
        }
    }

    private void setChief(Squadron squadron, Map<String, String> attributes) {
        // update chief if present
        if (attributes.containsKey("chief")) {
            if (attributes.get("chief") == null) {
                squadron.setChief(null);
            }
            else {
                Airman airman = airmanRepo.findById(UUID.fromString(attributes.get("chief")))
                        .orElseThrow(() -> new RecordNotFoundException("Provided chief UUID does not match any existing records"));

                squadron.setChief(airman);
            }
        }
    }

    private void setBaseName(Squadron squadron, Map<String, String> attributes) {
        // update base name if present
        if (attributes.containsKey("baseName")) {
            if (attributes.get("baseName") == null) {
                squadron.setBaseName(null);
            }
            else {
                squadron.setBaseName(attributes.get("baseName"));
            }
        }
    }

    private void setMajorCommand(Squadron squadron, Map<String, String> attributes) {
        // update major command if present
        if (attributes.containsKey("majorCommand")) {
            if (attributes.get("majorCommand") == null) {
                squadron.setMajorCommand(null);
            }
            else {
                squadron.setMajorCommand(attributes.get("majorCommand"));
            }
        }
    }
}

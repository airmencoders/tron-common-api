package mil.tron.commonapi.service;

import mil.tron.commonapi.entity.Airman;
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

    private SquadronRepository squadronRepo;
    private AirmanRepository airmanRepo;

    public SquadronServiceImpl(SquadronRepository squadronRepo, AirmanRepository airmanRepo) {
        this.squadronRepo = squadronRepo;
        this.airmanRepo = airmanRepo;
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
    public Squadron modifySquadronAttribs(UUID squadronId, Map<String, String> attributes) {
        Squadron squadron = squadronRepo.findById(squadronId).orElseThrow(
                () -> new RecordNotFoundException("Provided squadron UUID does not match any existing records"));


        // update leader if present
        if (attributes.containsKey("leader")) {
            if (attributes.get("leader") == null) {
                squadron.setLeader(null);
            }
            else {
                Airman airman = airmanRepo.findById(UUID.fromString(attributes.get("leader")))
                        .orElseThrow(() -> new RecordNotFoundException("Provided leader UUID does not match any existing records"));

                squadron.setLeader(airman);
            }
        }

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

        // update base name if present
        if (attributes.containsKey("baseName")) {
            if (attributes.get("baseName") == null) {
                squadron.setBaseName(null);
            }
            else {
                squadron.setBaseName(attributes.get("baseName"));
            }
        }

        // update major command if present
        if (attributes.containsKey("majorCommand")) {
            if (attributes.get("majorCommand") == null) {
                squadron.setMajorCommand(null);
            }
            else {
                squadron.setMajorCommand(attributes.get("majorCommand"));
            }
        }

        return squadronRepo.save(squadron);
    }
}

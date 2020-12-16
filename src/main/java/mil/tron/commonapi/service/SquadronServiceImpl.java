package mil.tron.commonapi.service;

import mil.tron.commonapi.airman.Airman;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.AirmanRepository;
import mil.tron.commonapi.repository.SquadronRepository;
import mil.tron.commonapi.entity.Squadron;
import org.springframework.stereotype.Service;

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
    public Squadron modifyLeader(UUID squadronId, UUID airmanId) {

        Squadron squadron = squadronRepo.findById(squadronId).orElseThrow(
                () -> new RecordNotFoundException("Provided squadron UUID does not match any existing records"));

        Airman airman = airmanRepo.findById(airmanId).orElseThrow(
                () -> new RecordNotFoundException("Provided airman UUID does not match any existing records"));

        squadron.setLeader(airman);
        squadronRepo.save(squadron);
        return squadron;
    }
}

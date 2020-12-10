package mil.tron.commonapi.service;

import mil.tron.commonapi.repository.SquadronRepository;
import mil.tron.commonapi.squadron.Squadron;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SquadronServiceImpl implements SquadronService {

    @Autowired
    private SquadronRepository squadronRepo;

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

        return null;
    }

    @Override
    public Squadron updateSquadron(UUID id, Squadron squadron) {
        if (!squadronRepo.existsById(id)) {
            return null;
        }

        // the squadrons object's id better match the id given,
        //  otherwise hibernate will save under whatever id's inside the object
        if (!squadron.getId().equals(id)) {
            return null;
        }

        return squadronRepo.save(squadron);
    }

    @Override
    public void removeSquadron(UUID id) {
        if (squadronRepo.existsById(id)) {
            squadronRepo.deleteById(id);
        }
    }

    @Override
    public Iterable<Squadron> getAllSquadrons() {
        return squadronRepo.findAll();
    }

    @Override
    public Squadron getSquadron(UUID id) {
        return squadronRepo.findById(id).orElse(null);
    }
}

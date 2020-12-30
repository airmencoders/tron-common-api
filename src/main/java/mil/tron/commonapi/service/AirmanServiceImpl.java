package mil.tron.commonapi.service;

import mil.tron.commonapi.entity.Airman;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.AirmanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AirmanServiceImpl implements AirmanService {

    @Autowired
    private AirmanRepository airmanRepo;

    @Override
    public Airman createAirman(Airman airman) {

        if (airman.getId() == null) {
            // we have to generate an ID manually since we're not using normal
            //  serial ID but rather an UUID for Person entity...
            airman.setId(UUID.randomUUID());
        }

        /**
         * Borrows check from Person service...
         */
        if(airman.getEmail() != null && airmanRepo.findByEmailIgnoreCase(airman.getEmail()).isPresent())
            throw new ResourceAlreadyExistsException(String.format("Airman with the email: %s already exists", airman.getEmail()));

        // the record with this 'id' shouldn't already exist...
        if (!airmanRepo.existsById(airman.getId())) {
            return airmanRepo.save(airman);
        }

        throw new ResourceAlreadyExistsException("Airman with the id: " + airman.getId() + " already exists.");
    }

    @Override
    public Airman updateAirman(UUID id, Airman airman) throws InvalidRecordUpdateRequest, RecordNotFoundException {

        Airman dbAirman = airmanRepo.findById(id).orElseThrow(() ->
                new RecordNotFoundException("Provided airman UUID: " + id.toString() + " does not match any existing records")
        );

        // the airman object's id better match the id given,
        //  otherwise hibernate will save under whatever id's inside the object
        if (!airman.getId().equals(id)) {
            throw new InvalidRecordUpdateRequest(
                    "Provided airman UUID " + airman.getId() + " mismatched UUID in airman object");
        }

        /**
         * Borrows check from Person service...
         */
        String dbAirmanEmail = dbAirman.getEmail();
        String airmanEmail = airman.getEmail();
        if (airmanEmail != null && !airmanEmail.equalsIgnoreCase(dbAirmanEmail) && airmanRepo.findByEmailIgnoreCase(airmanEmail).isPresent())
            throw new InvalidRecordUpdateRequest(String.format("Airman Email: %s is already in use.", airman.getEmail()));

        return airmanRepo.save(airman);
    }

    @Override
    public void removeAirman(UUID id) throws RecordNotFoundException  {
        if (airmanRepo.existsById(id)) {
            airmanRepo.deleteById(id);
        }
        else {
            throw new RecordNotFoundException("Airman record with UUID: " + id.toString() + " does not exist");
        }
    }

    @Override
    public Iterable<Airman> getAllAirman() {
        return airmanRepo.findAll();
    }

    @Override
    public Airman getAirman(UUID id) {
        return airmanRepo.findById(id).orElseThrow(() -> new RecordNotFoundException("Airman with resource ID: " + id.toString() + " does not exist."));
    }

    @Override
    public List<Airman> bulkAddAirmen(List<Airman> airmen) {
        List<Airman> addedAirmen = new ArrayList<>();
        for (Airman a : airmen) {
            addedAirmen.add(this.createAirman(a));
        }
        return addedAirmen;
    }
}

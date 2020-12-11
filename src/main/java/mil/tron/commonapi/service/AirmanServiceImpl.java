package mil.tron.commonapi.service;

import mil.tron.commonapi.airman.Airman;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.AirmanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.Parameter;
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
        // the record with this 'id' shouldn't already exist...
        if (!airmanRepo.existsById(airman.getId())) {

            return airmanRepo.save(airman);
        }

        return null;
    }

    @Override
    public Airman updateAirman(UUID id, Airman airman) throws InvalidRecordUpdateRequest, RecordNotFoundException {
        if (!airmanRepo.existsById(id)) {
            throw new RecordNotFoundException("Provided airman UUID does not match any existing records");
        }

        // the airman object's id better match the id given,
        //  otherwise hibernate will save under whatever id's inside the object
        if (!airman.getId().equals(id)) {
            throw new InvalidRecordUpdateRequest(
                    "Provided airman UUID mismatched UUID in airman object");
        }

        return airmanRepo.save(airman);
    }

    @Override
    public void removeAirman(UUID id) throws RecordNotFoundException  {
        if (airmanRepo.existsById(id)) {
            airmanRepo.deleteById(id);
        }
        else {
            throw new RecordNotFoundException("Airman record with provided UUID does not exist");
        }
    }

    @Override
    public Iterable<Airman> getAllAirman() {
        return airmanRepo.findAll();
    }

    @Override
    public Airman getAirman(UUID id) {
        return airmanRepo.findById(id).orElse(null);
    }
}

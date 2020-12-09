package mil.tron.commonapi.service;

import mil.tron.commonapi.airman.Airman;
import mil.tron.commonapi.repository.AirmanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AirmanServiceImpl implements AirmanService {

    @Autowired
    private AirmanRepository airmanRepo;

    @Override
    public Airman createAirman(Airman airman) {
        return airmanRepo.save(airman);
    }

    @Override
    public Airman updateAirman(UUID id, Airman airman) {
        if (!airmanRepo.existsById(id)) {
            return null;
        }

        // the airman object's id better match the id given
        //  otherwise hibernate will save under whatever id's in the object
        if (!airman.getId().equals(id)) {
            return null;
        }

        return airmanRepo.save(airman);
    }

    @Override
    public void removeAirman(UUID id) {
        if (airmanRepo.existsById(id)) {
            airmanRepo.deleteById(id);
        }
    }

    @Override
    public Iterable<Airman> getAllAirman() {
        return airmanRepo.findAll();
    }

    @Override
    public Airman getAirman(UUID id) {
        return airmanRepo.findById(id);
    }
}

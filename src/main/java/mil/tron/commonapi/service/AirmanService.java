package mil.tron.commonapi.service;

import mil.tron.commonapi.airman.Airman;

import java.util.UUID;

public interface AirmanService {
    Airman createAirman(Airman airman);

    Airman updateAirman(UUID id, Airman airman);
    void removeAirman(UUID id);
    Iterable<Airman> getAllAirman();
    Airman getAirman(UUID id);
}

package mil.tron.commonapi.service;

import mil.tron.commonapi.airman.Airman;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;

import java.util.UUID;

public interface AirmanService {
    public abstract Airman createAirman(Airman airman);

    public abstract Airman updateAirman(UUID id, Airman airman);
    public abstract void removeAirman(UUID id);
    public abstract Iterable<Airman> getAllAirman();
    public abstract Airman getAirman(UUID id);
}

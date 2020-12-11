package mil.tron.commonapi.service;

import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.squadron.Squadron;

import java.util.UUID;

public interface SquadronService {
    public abstract Squadron createSquadron(Squadron squadron);
    public abstract Squadron updateSquadron(UUID id, Squadron squadron)
            throws InvalidRecordUpdateRequest, RecordNotFoundException;

    public abstract void removeSquadron(UUID id) throws RecordNotFoundException;

    public abstract Iterable<Squadron> getAllSquadrons();
    public abstract Squadron getSquadron(UUID id);
}

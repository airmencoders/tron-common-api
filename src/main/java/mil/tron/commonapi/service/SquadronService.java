package mil.tron.commonapi.service;

import mil.tron.commonapi.squadron.Squadron;

import java.util.UUID;

public interface SquadronService {
    Squadron createSquadron(Squadron squadron);
    Squadron updateSquadron(UUID id, Squadron squadron);
    void removeSquadron(UUID id);
    Iterable<Squadron> getAllSquadrons();
    Squadron getSquadron(UUID id);
}

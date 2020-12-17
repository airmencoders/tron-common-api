package mil.tron.commonapi.service;

import mil.tron.commonapi.entity.Squadron;

import java.util.Map;
import java.util.UUID;

public interface SquadronService {
    Squadron createSquadron(Squadron squadron);
    Squadron updateSquadron(UUID id, Squadron squadron);
    void removeSquadron(UUID id);
    Iterable<Squadron> getAllSquadrons();
    Squadron getSquadron(UUID id);

    Squadron modifySquadronAttributes(UUID squadronId, Map<String, String> attributes);
}

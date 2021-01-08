package mil.tron.commonapi.service;

import mil.tron.commonapi.dto.SquadronDto;
import mil.tron.commonapi.entity.Squadron;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface SquadronService {
    Squadron findSquadron(UUID id);
    Squadron removeMember(UUID organizationId, List<UUID> personIds);
    Squadron addMember(UUID organizationId, List<UUID> personIds);
    Squadron modify(UUID squadronId, Map<String, String> attribs);

    // methods dealing only with DTO
    SquadronDto createSquadron(SquadronDto squadron);
    SquadronDto updateSquadron(UUID id, SquadronDto squadron);
    void removeSquadron(UUID id);
    Iterable<SquadronDto> getAllSquadrons();
    SquadronDto getSquadron(UUID id);

    SquadronDto modifySquadronAttributes(UUID squadronId, Map<String, String> attributes);
    SquadronDto removeSquadronMember(UUID squadronId, List<UUID> airmanIds);
    SquadronDto addSquadronMember(UUID squadronId, List<UUID> airmanId);

    List<SquadronDto> bulkAddSquadrons(List<SquadronDto> newSquadrons);

    SquadronDto convertToDto(Squadron squadron);
    Squadron convertToEntity(SquadronDto squadronDto);
}

package mil.tron.commonapi.service.puckboard;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.UUID;

public interface PuckboardExtractorService {

    Map<String, Map<UUID, String>> persistOrgsAndMembers(
            JsonNode orgs,
            JsonNode people,
            JsonNode branchInfoJson);
}

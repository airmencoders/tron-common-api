package mil.tron.commonapi.service.scratch;

import java.util.UUID;

public interface JsonDbService {
    Object addElement(UUID appId, String tableName, Object json);
    void removeElement(UUID appId, String tableName, String path);
    Object updateElement(UUID appId, String tableName, Object id, Object json, String path);
    Object queryJson(UUID appId, String tableName, String path);

}

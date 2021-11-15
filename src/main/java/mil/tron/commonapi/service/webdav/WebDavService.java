package mil.tron.commonapi.service.webdav;

import java.util.UUID;

public interface WebDavService {

    String propFind(UUID spaceId, String path, boolean children);
    String mkCol(UUID spaceId, String path);
}

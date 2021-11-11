package mil.tron.commonapi.service.webdav;

import mil.tron.commonapi.dto.dav.PropFindDto;

import java.util.UUID;

public interface WebDavService {

    PropFindDto propFind(UUID spaceId, String path);
}

package mil.tron.commonapi.service.webdav;

import mil.tron.commonapi.dto.dav.*;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;
import mil.tron.commonapi.service.documentspace.DocumentSpaceFileSystemServiceImpl;
import mil.tron.commonapi.service.documentspace.DocumentSpaceService;
import mil.tron.commonapi.service.documentspace.DocumentSpaceServiceImpl;
import mil.tron.commonapi.service.documentspace.util.FilePathSpecWithContents;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class WebDavServiceImpl implements WebDavService {

    private final DocumentSpaceService documentSpaceService;

    public WebDavServiceImpl(DocumentSpaceService documentSpaceService) {
        this.documentSpaceService = documentSpaceService;
    }

    @Override
    public PropFindDto propFind(UUID spaceId, String path) {

        // get the path contents from the service
        FilePathSpecWithContents content = documentSpaceService.getFolderContents(spaceId, path);

        List<PropFindResponse> props = new ArrayList<>();
        props.add(PropFindResponse.builder()
                .propStat(PropStat.builder()
                        .prop(Prop.builder()
                                .contentLength(Optional.of(0L))
                                .lastModified("")
                                .creationDate("")
                                .resourceType(new PropResourceType())
                                .build())
                        .status("HTTP/1.1 200 OK")
                        .build())
                .href(DocumentSpaceFileSystemServiceImpl.joinPathParts("/api/v2/document-space/space/" + spaceId + "/" + content.getFullPathSpec() + "/"))
                .build());

        for (DocumentSpaceFileSystemEntry entry : content.getEntries()) {
            props.add(PropFindResponse.builder()
                    .propStat(PropStat.builder()
                            .prop(Prop.builder()
                                    .contentLength(Optional.of(entry.getSize()))
                                    .lastModified(entry.getLastModifiedOn() != null ? entry.getLastModifiedOn().toString() : "")
                                    .creationDate(entry.getCreatedOn() != null ? entry.getCreatedOn().toString() : "")
                                    .resourceType(new PropResourceType())
                                    .build())
                            .status("HTTP/1.1 200 OK")
                            .build())
                    .href(DocumentSpaceFileSystemServiceImpl.joinPathParts("/api/v2/document-space/space/" + spaceId + "/" + content.getFullPathSpec() + "/" + entry.getItemName()))
                    .build());
        }

        return PropFindDto.builder().responses(props).build();
    }
}

package mil.tron.commonapi.service.documentspace;

import com.google.common.collect.Lists;
import lombok.*;
import lombok.experimental.SuperBuilder;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Utility class to represent a DocumentSpaceFileSystemEntry in a more friendly
 * and informative way (much like a DTO), where we have some helpful path information about
 * its parent (etc)
 */
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class FilePathSpec {
    /**
     * The UUID of the parent element that owns this element
     */
    @Getter
    @Setter
    @Builder.Default
    private UUID parentFolderId = UUID.fromString(DocumentSpaceFileSystemEntry.NIL_UUID);

    /**
     * The full 'unix-like' path string leading up to and including this element (excluding the document space UUID)
     */
    @Getter
    @Setter
    @Builder.Default
    private String fullPathSpec = "";

    /**
     * List of UUIDs leading up to and including this element
     */
    @Getter
    @Setter
    @Builder.Default
    private List<UUID> uuidList = Lists.newArrayList(UUID.fromString(DocumentSpaceFileSystemEntry.NIL_UUID));

    /**
     * The owning document space
     */
    @Getter
    @Setter
    @NotNull
    private UUID documentSpaceId;

    /**
     * The actual item UUID (of the folder etc) we're concerned about
     */
    @Getter
    @Setter
    @Builder.Default
    @NotNull
    private UUID itemId = UUID.randomUUID();

    /**
     * The actual item name (of the folder etc) we're concerned about
     */
    @NotBlank
    @NotNull
    @Getter
    @Setter
    private String itemName;

    private String docSpaceQualifiedPath;

    /**
     * Returns the minio-ready-path up to and including this element
     * example - `doc-space-uuid/element-uuid/element-uuid
     * @return minio-ready path string up to and including this element
     */
    public String getDocSpaceQualifiedPath() {
        String path = this.getDocumentSpaceId() + "/" + this.getUuidList()
                .stream()
                .map(UUID::toString)
                .collect(Collectors.joining(DocumentSpaceFileSystemServiceImpl.PATH_SEP));

        if (!path.endsWith(DocumentSpaceFileSystemServiceImpl.PATH_SEP)) path += DocumentSpaceFileSystemServiceImpl.PATH_SEP;

        return path;
    }

}

package mil.tron.commonapi.service.documentspace;

import com.sun.istack.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;
import org.assertj.core.util.Lists;

import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Utility class to represent a DocumentSpaceFileSystemEntry in a more friendly
 * and informative way (much like a DTO), where we have some helpful path information about
 * its parent (etc)
 */

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class FilePathSpec {
    /**
     * The UUID of the parent element that owns this element
     */
    @Builder.Default
    private UUID parentFolderId = UUID.fromString(DocumentSpaceFileSystemEntry.NIL_UUID);

    /**
     * The full 'unix-like' path string leading up to and including this element (excluding the document space UUID)
     */
    @Builder.Default
    private String fullPathSpec = "";

    /**
     * List of UUIDs leading up to and including this element
     */
    @Builder.Default
    private List<UUID> uuidList = Lists.newArrayList(UUID.fromString(DocumentSpaceFileSystemEntry.NIL_UUID));

    /**
     * The owning document space
     */
    @NotNull
    private UUID documentSpaceId;

    /**
     * The actual item UUID (of the folder etc) we're concerned about
     */
    @Builder.Default
    @NotNull
    private UUID itemId = UUID.randomUUID();

    /**
     * The actual item name (of the folder etc) we're concerned about
     */
    @NotBlank
    @NotNull
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

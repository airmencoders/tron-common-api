package mil.tron.commonapi.service.documentspace.util;

import com.google.common.collect.Lists;
import lombok.*;
import lombok.experimental.SuperBuilder;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;
import mil.tron.commonapi.service.documentspace.DocumentSpaceFileSystemServiceImpl;

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
    private UUID parentFolderId = DocumentSpaceFileSystemEntry.NIL_UUID;

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
    private List<UUID> uuidList = Lists.newArrayList(DocumentSpaceFileSystemEntry.NIL_UUID);

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

    /**
     * Returns the minio-ready-path up to and including this element (treating `element` like a folder)
     * example - `doc-space-uuid/element-uuid/element-uuid
     * @return minio-ready path string up to and including this folder
     */
    public String getDocSpaceQualifiedPath() {
        String path = this.getDocumentSpaceId() + "/" + this.getUuidList()
                .stream()
                .map(UUID::toString)
                .collect(Collectors.joining(DocumentSpaceFileSystemServiceImpl.PATH_SEP));

        if (!path.endsWith(DocumentSpaceFileSystemServiceImpl.PATH_SEP)) path += DocumentSpaceFileSystemServiceImpl.PATH_SEP;

        return path;
    }

    /**
     * Returns the minio-ready-path up to and including this element (treating `element` like a file and returning its plain-english name)
     * since we store files in S3 by their plain-english name, unlike with folders
     * example - `doc-space-uuid/element-uuid1/element-uuid2/element-uuid3` returns `doc-space-uuid/element-uuid1/element-uuid2/names.txt`
     * @return minio-ready path string up to and including this file
     */
    public String getDocSpaceQualifiedFilePath() {
        List<UUID> list = new ArrayList<>();
        if (!this.getUuidList().isEmpty()) {
            list.addAll(this.getUuidList());
            list.remove(list.size() - 1);
        }
        String path = this.getDocumentSpaceId() + "/" + list
                .stream()
                .map(UUID::toString)
                .collect(Collectors.joining(DocumentSpaceFileSystemServiceImpl.PATH_SEP));

        if (!path.endsWith(DocumentSpaceFileSystemServiceImpl.PATH_SEP)) path += DocumentSpaceFileSystemServiceImpl.PATH_SEP;

        return path + this.getItemName();

    }

}

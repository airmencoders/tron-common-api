package mil.tron.commonapi.service.documentspace.util;

import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to hold a linked list of a file system hierarchy
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FileSystemElementTree {

    /**
     * The entity that represents this folder element in the system database
     */
    @Getter
    @Setter
    private DocumentSpaceFileSystemEntry value;

    /**
     * The file path spec of this sub folder that contains information about its
     * path, parent, and uuid path
     */
    @Getter
    @Setter
    private FilePathSpec filePathSpec;

    /**
     * Folders immediately underneath this subfolder that this folder is a parent of
     */
    @Getter
    @Setter
    @Builder.Default
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<FileSystemElementTree> nodes = new ArrayList<>();  // these are folders (sub folders)

    /**
     * The list of files immediately underneath this folder (contained in this folder) one-level deep
     * This field is a list of the actual S3 objects themselves whose keys are of the following
     * naming format (/doc-space-uuid/subfolder-uuid.../filename)
     */
    @Getter
    @Setter
    private List<S3ObjectSummary> files = new ArrayList<>();

    public void addNode(FileSystemElementTree entry) {
        this.nodes.add(entry);
    }
}

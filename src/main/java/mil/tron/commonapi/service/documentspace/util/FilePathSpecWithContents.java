package mil.tron.commonapi.service.documentspace.util;

import com.amazonaws.services.s3.model.S3ObjectSummary;
import lombok.*;
import lombok.experimental.SuperBuilder;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Like FilePathSpec, but we include the sub dirs contained herein and files
 */
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class FilePathSpecWithContents extends FilePathSpec {

    @Getter
    @Setter
    private List<S3ObjectSummary> s3Objects = new ArrayList<>();

    /**
     * List of file (string file names) in this folder (one-level deep)
     */
    @Getter
    @Setter
    @Builder.Default
    private List<String> files = new ArrayList<>();

    /**
     * List of sub-folders in this folder (one-level deep)
     */
    @Getter
    @Setter
    @Builder.Default
    private List<DocumentSpaceFileSystemEntry> subFolderElements = new ArrayList<>();
}

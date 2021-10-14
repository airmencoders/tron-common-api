package mil.tron.commonapi.service.documentspace.util;

import com.amazonaws.services.s3.model.S3Object;
import lombok.*;

/**
 * Utility class to hold a file's S3 object qualified path (in the eyes of Minio/S3) [like /doc-space-id/subfolder-uuid/filename]
 * and the fully qualified path as the user sees it in the Document Space [like /docs/file.txt]..
 *
 * Used primarily for creating the zip file when we need to know the real S3 path spec and the plain-english one for the zip file contents
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class S3ObjectAndFilename {

    @Getter
    @Setter
    private String pathAndFilename;

    public String getPathAndFileNameWithoutLeadingSlash() {
        return this.pathAndFilename.replaceFirst("^/", "");
    }

    @Getter
    @Setter
    private S3Object s3Object;

}

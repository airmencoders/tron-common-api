package mil.tron.commonapi.dto.documentspace;

import lombok.*;
import mil.tron.commonapi.validations.ValidDocSpaceFolderOrFilename;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * Used for folder renaming
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DocumentSpaceRenameFileDto {
    @NotNull
    @Getter
    @Setter
    private String filePath;

    @NotNull
    @Getter
    @Setter
    private String existingFilename;

    /**
     * The proposed new file name
     */
    @Size(max = 255)
    @ValidDocSpaceFolderOrFilename
    @NotNull
    @NotBlank
    @Getter
    @Setter
    private String newName;
}

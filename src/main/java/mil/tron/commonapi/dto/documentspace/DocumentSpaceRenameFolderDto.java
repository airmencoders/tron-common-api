package mil.tron.commonapi.dto.documentspace;

import lombok.*;
import mil.tron.commonapi.validations.ValidDocSpaceFolderOrFilename;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * Used for folder renaming
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DocumentSpaceRenameFolderDto {
    @NotNull
    @Getter
    @Setter
    private String existingFolderPath;

    /**
     * The proposed new folder name
     */
    @ValidDocSpaceFolderOrFilename
    @NotNull
    @NotBlank
    @Getter
    @Setter
    private String newName;
}

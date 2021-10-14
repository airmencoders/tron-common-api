package mil.tron.commonapi.dto.documentspace;

import lombok.*;
import lombok.experimental.SuperBuilder;
import mil.tron.commonapi.validations.ValidDocSpaceFolderOrFilename;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * Used for folder creation
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DocumentSpaceCreateFolderDto {
    @NotNull
    @Getter
    @Setter
    private String path;

    /**
     * The proposed new folder name
     */
    @ValidDocSpaceFolderOrFilename
    @NotNull
    @NotBlank
    @Getter
    @Setter
    private String folderName;
}

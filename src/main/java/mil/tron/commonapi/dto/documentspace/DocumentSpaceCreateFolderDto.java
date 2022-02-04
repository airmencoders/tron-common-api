package mil.tron.commonapi.dto.documentspace;

import lombok.*;
import mil.tron.commonapi.validations.ValidDocSpaceFolderOrFilename;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

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
    @Size(max = 255)
    @ValidDocSpaceFolderOrFilename
    @NotNull
    @NotBlank
    @Getter
    private String folderName;

    public void setFolderName(String name) { //NOSONAR
        if (name != null) {
            this.folderName = name.trim();
        }
    }
}

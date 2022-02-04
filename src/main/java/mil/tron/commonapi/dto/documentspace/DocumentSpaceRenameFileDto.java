package mil.tron.commonapi.dto.documentspace;

import lombok.*;
import mil.tron.commonapi.validations.ValidDocSpaceFolderOrFilename;
import org.springframework.security.core.parameters.P;

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
    private String existingFilename;

    public void setExistingFileName(String name) {  //NOSONAR
        if (name != null) {
            this.existingFilename = name.trim();
        }
    }

    /**
     * The proposed new file name
     */
    @Size(max = 255)
    @ValidDocSpaceFolderOrFilename
    @NotNull
    @NotBlank
    @Getter
    private String newName;

    public void setNewName(String name) { //NOSONAR
        if (name != null) {
            this.newName = name.trim();
        }
    }
}

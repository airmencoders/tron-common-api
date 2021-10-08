package mil.tron.commonapi.dto.documentspace;

import lombok.*;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Data
public class DocumentSpacePathSpecDto {
    @NotNull
    @Getter
    @Setter
    private String path;

    @NotNull
    @NotBlank
    @Getter
    @Setter
    private String folderName;
}

package mil.tron.commonapi.dto.documentspace.mobile;


import lombok.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class DocumentSpaceMobileResponseDto {

    /**
     * UI version of the privs user has
     * VIEWER - (READ, which is implicitly given)
     * EDITOR - (WRITE and READ)
     * ADMIN - (WRITE, READ, MEMBERSHIP)
     */
    public enum DocumentSpaceShortPrivilege {
        VIEWER,
        EDITOR,
        ADMIN
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    @Builder
    public static class SpaceInfo {
        @NotNull
        private UUID id;

        @NotBlank
        @NotNull
        private String name;

        private DocumentSpaceShortPrivilege privilege;
    }

    private List<SpaceInfo> spaces;
    private SpaceInfo defaultSpace;
}

package mil.tron.commonapi.dto.organizations;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import mil.tron.commonapi.dto.OrganizationDto;

public class Wing extends OrganizationDto {
    @Schema(nullable = true)
    @Getter
    @Setter
    private String pas;
}

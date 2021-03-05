package mil.tron.commonapi.dto.organizations;

import lombok.*;
import mil.tron.commonapi.dto.OrganizationDto;

public class Flight extends OrganizationDto {
    @Getter
    @Setter
    private String pas;
}

package mil.tron.commonapi.dto.organizations;

import lombok.*;
import mil.tron.commonapi.dto.OrganizationDto;

public class Group extends OrganizationDto {
    @Getter
    @Setter
    private String pas;
}

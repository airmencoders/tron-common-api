package mil.tron.commonapi.dto;

import lombok.*;
import mil.tron.commonapi.entity.Person;

import java.util.UUID;

/**
 * Mirror Squadron entity but only shows UUIDs for nested Airmen/Sqdns
 */

@AllArgsConstructor
@NoArgsConstructor
public class SquadronTerseDto extends OrganizationTerseDto {

    @Getter
    private UUID operationsDirector;

    @Getter
    private UUID chief;

    @Getter
    @Setter
    private String baseName;

    @Getter
    @Setter
    private String majorCommand;

    public void setOperationsDirector(Person p) {
        if (p != null) {
            this.operationsDirector = p.getId();
        }
    }

    public void setChief(Person p) {
        if (p != null) {
            this.chief = p.getId();
        }
    }
}

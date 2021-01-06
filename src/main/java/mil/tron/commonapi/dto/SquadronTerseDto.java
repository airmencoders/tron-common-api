package mil.tron.commonapi.dto;

import lombok.*;
import mil.tron.commonapi.entity.Person;

import java.util.UUID;

/**
 * Mirror Squadron entity but only shows UUIDs for nested Airmen/Sqdns
 */

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class SquadronTerseDto extends OrganizationTerseDto {

    private UUID operationsDirector;
    private UUID chief;
    private String baseName;
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

package mil.tron.commonapi.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.*;
import mil.tron.commonapi.entity.Person;

import java.util.UUID;

/**
 * Mirror Squadron entity but only shows UUIDs for nested Airmen/Sqdns
 */

@AllArgsConstructor
@NoArgsConstructor
public class SquadronDto extends OrganizationDto {

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

    /**
     * Used by Jackson on the operationsDirector field during JSON deserialization
     * @param id
     */
    @JsonSetter("operationsDirector")
    public void setOpsDirectorUUID(UUID id) {
        this.operationsDirector = id;
    }

    /**
     * Used by Jackson on the chief field during JSON deserialization
     * @param id
     */
    @JsonSetter("chief")
    public void setChief(UUID id) {
        this.chief = id;
    }

    /**
     * Used by model mapper during DTO conversion process
     * @param p
     */
    public void setOperationsDirector(Person p) {
        if (p != null) {
            this.operationsDirector = p.getId();
        }
    }

    /**
     * Used by model mapper during DTO conversion process
     * @param p
     */
    public void setChief(Person p) {
        if (p != null) {
            this.chief = p.getId();
        }
    }
}

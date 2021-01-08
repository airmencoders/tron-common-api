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
    public void setOperationsDirector(Object p) {
        if (p instanceof Person) { // Object, so that this can work for all types of Persons
            Person person = (Person) p;
            this.operationsDirector = person.getId();
        }
    }

    /**
     * Used by model mapper during DTO conversion process
     * @param p
     */
    public void setChief(Object p) { // Object, so that this can work for all types of Persons
        if (p instanceof Person) {
            Person person = (Person) p;
            this.chief = person.getId();
        }
    }
}

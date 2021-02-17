package mil.tron.commonapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.validations.ValidDodId;
import mil.tron.commonapi.validations.ValidPhoneNumber;

import javax.validation.constraints.Email;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
public class PersonDto {

    @Getter
    @Setter
    @Builder.Default
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private UUID id = UUID.randomUUID();

    @Getter
    @Setter
    private String firstName;

    @Getter
    @Setter
    private String middleName;

    @Getter
    @Setter
    private String lastName;

    /**
     * The title of a person, as in how they should be addressed.
     * Examples: Mr., Ms., Dr., SSgt, PFC, PO2, LCpl
     */
    @Getter
    @Setter
    private String title;

    @Email(message = "Malformed email address")
    @Getter
    @Setter
    private String email;

    /**
     * An 10-digit airman's DOD Identification number.
     */
    @Getter
    @Setter
    @ValidDodId
    private String dodid;

    /**
     * Service member's rank
     */
    @Getter
    @Setter
    private String rank;

    @Getter
    @Setter
    private Branch branch;

    @Getter
    @Setter
    private String phone;

    @Getter
    @Setter
    private String address;

    @Getter
    @Setter
    @ValidPhoneNumber
    private String dutyPhone;

    @Getter
    @Setter
    private String dutyTitle;

    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, String> meta;

    @JsonIgnore
    public String getFullName() {
        return String.format("%s %s", firstName, lastName);
    }

    @JsonIgnore
    public String getMetaProperty(String property) {
        return meta != null ? meta.get(property) : null;
    }

    @JsonIgnore
    public PersonDto setMetaProperty(String property, String value) {
        if (meta == null) {
            meta = new HashMap<>();
        }
        meta.put(property, value);
        return this;
    }
}

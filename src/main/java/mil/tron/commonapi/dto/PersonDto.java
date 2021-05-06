package mil.tron.commonapi.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import mil.tron.commonapi.annotation.security.PiiField;
import mil.tron.commonapi.dto.persons.*;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.validations.ValidDodId;
import mil.tron.commonapi.validations.ValidPhoneNumber;

import javax.validation.constraints.Email;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
@Schema(
        type = "object",
        title = "Person",
        subTypes = { Airman.class, CoastGuardsman.class, Marine.class, Sailor.class, Soldier.class, Spaceman.class },
        discriminatorProperty = "branch",
        discriminatorMapping = {
                @DiscriminatorMapping(value = "USAF", schema = Airman.class),
                @DiscriminatorMapping(value = "USCG", schema = CoastGuardsman.class),
                @DiscriminatorMapping(value = "USMC", schema = Marine.class),
                @DiscriminatorMapping(value = "USN", schema = Sailor.class),
                @DiscriminatorMapping(value = "USA", schema = Soldier.class),
                @DiscriminatorMapping(value = "USSF", schema = Spaceman.class),
                @DiscriminatorMapping(value = "OTHER", schema = PersonDto.class),
        }
)
public class PersonDto {

    @Getter
    @Setter
    @Builder.Default
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private UUID id = UUID.randomUUID();

    /**
     * The person's first (given) name
     */
    @Schema(nullable = true)
    @PiiField
    @Getter
    @Setter
    private String firstName;

    /**
     * The person's middle name
     */
    @Schema(nullable = true)
    @PiiField
    @Getter
    @Setter
    private String middleName;

    /**
     * The person's last (family) name
     */
    @Schema(nullable = true)
    @PiiField
    @Getter
    @Setter
    private String lastName;

    /**
     * The title of a person, as in how they should be addressed.
     * Examples: Mr., Ms., Dr., SSgt, PFC, PO2, LCpl
     */
    @Schema(nullable = true)
    @Getter
    @Setter
    private String title;

    /**
     * The person's email address
     */
    @PiiField
    @Email(message = "Malformed email address")
    @Getter
    @Setter
    private String email;

    /**
     * An 10-digit airman's DOD Identification number.
     */
    @Schema(nullable = true)
    @PiiField
    @Getter
    @Setter
    @ValidDodId
    private String dodid;

    /**
     * The person's rank (abbreviation, ex: TSgt, SSgt, Capt, Col etc.)
     */
    @Getter
    @Setter
    private String rank;

    /**
     * The person's service branch
     */
    @Getter
    @Setter
    private Branch branch;

    /**
     * The person's phone number
     */
    @Schema(nullable = true)
    @PiiField
    @Getter
    @Setter
    @ValidPhoneNumber
    private String phone;

    /**
     * The person's address
     */
    @Schema(nullable = true)
    @Getter
    @Setter
    private String address;

    /**
     * The person's duty (work) phone number
     */
    @Schema(nullable = true)
    @Getter
    @Setter
    @ValidPhoneNumber
    private String dutyPhone;

    /**
     * The person's official job title
     */
    @Schema(nullable = true)
    @Getter
    @Setter
    private String dutyTitle;

    /**
     * The primary organization for this person
     */
    @Schema(nullable = true)
    @Getter
    @Setter
    private UUID primaryOrganizationId;

    /**
     * The organizations this person is a member of
     */
    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Set<UUID> organizationMemberships;

    /**
     * The organizations this person is the leader of
     */
    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Set<UUID> organizationLeaderships;

    @JsonIgnore
    private Map<String, String> meta;

    @JsonAnyGetter
    public Map<String, String> getMeta() {
        return meta;
    }

    @JsonIgnore
    public String getFullName() {
        return String.format("%s %s", firstName, lastName);
    }

    @JsonIgnore
    public String getMetaProperty(String property) {
        return meta != null ? meta.get(property) : null;
    }

    @JsonAnySetter
    public PersonDto setMetaProperty(String property, String value) {
        if (meta == null) {
            meta = new HashMap<>();
        }
        meta.put(property, value);
        return this;
    }

    public PersonDto removeMetaProperty(String property) {
        if (meta != null) {
            meta.remove(property);
        }
        return this;
    }
}

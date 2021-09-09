package mil.tron.commonapi.dto;

import com.fasterxml.jackson.annotation.*;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import mil.tron.commonapi.annotation.jsonpatch.NonPatchableField;
import mil.tron.commonapi.annotation.security.PiiField;
import mil.tron.commonapi.dto.persons.*;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.validations.NullOrNotBlankValidation;
import mil.tron.commonapi.validations.ValidDodId;
import mil.tron.commonapi.validations.ValidPhoneNumber;

import javax.validation.constraints.Email;
import javax.validation.constraints.Size;
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
	@JsonIgnore
	public static final String RANK_FIELD = "rank"; 
	@JsonIgnore
	public static final String ORG_MEMBERSHIPS_FIELD = "organizationMemberships";
	@JsonIgnore
	public static final String ORG_LEADERSHIPS_FIELD = "organizationLeaderships";
	@JsonIgnore
	public static final String BRANCH_FIELD = "branch";
	@JsonIgnore
	private static final String NULL_OR_NOT_BLANK_DESCRIPTION = "Must be null or not blank";
	@JsonIgnore
    private static final String FIELD_IS_READONLY_MSG = "This field is readonly and cannot be set via HTTP request";

    @Getter
    @Setter
    @Builder.Default
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private UUID id = UUID.randomUUID();

    /**
     * The person's first (given) name
     */
    @Schema(nullable = true,
    		description = NULL_OR_NOT_BLANK_DESCRIPTION)
    @PiiField
    @Size(max = 255)
    @NullOrNotBlankValidation
    @Getter
    @Setter
    private String firstName;

    /**
     * The person's middle name
     */
    @Schema(nullable = true,
    		description = NULL_OR_NOT_BLANK_DESCRIPTION)
    @PiiField
    @Size(max = 255)
    @NullOrNotBlankValidation
    @Getter
    @Setter
    private String middleName;

    /**
     * The person's last (family) name
     */
    @Schema(nullable = true,
    		description = NULL_OR_NOT_BLANK_DESCRIPTION)
    @PiiField
    @Size(max = 255)
    @NullOrNotBlankValidation
    @Getter
    @Setter
    private String lastName;

    /**
     * The title of a person, as in how they should be addressed.
     * Examples: Mr., Ms., Dr., SSgt, PFC, PO2, LCpl
     */
    @Schema(nullable = true,
    		description = NULL_OR_NOT_BLANK_DESCRIPTION)
    @Size(max = 255)
    @NullOrNotBlankValidation
    @Getter
    @Setter
    private String title;

    /**
     * The person's email address
     */
    @PiiField
    @Email(regexp = ".+@.+\\..+$", message = "Malformed email address")
    @Schema(nullable = true,
    		description = NULL_OR_NOT_BLANK_DESCRIPTION)
    @NullOrNotBlankValidation
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
    @ValidDodId(message = "An acceptable DODID must be 5-10 digits or a null value")
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
    @Schema(nullable = true,
    		description = NULL_OR_NOT_BLANK_DESCRIPTION)
    @NullOrNotBlankValidation
    @PiiField
    @Getter
    @Setter
    @ValidPhoneNumber
    private String phone;

    /**
     * The person's address
     */
    @Schema(nullable = true,
    		description = NULL_OR_NOT_BLANK_DESCRIPTION)
    @Size(max = 255)
    @NullOrNotBlankValidation
    @Getter
    @Setter
    private String address;

    /**
     * The person's duty (work) phone number
     */
    @Schema(nullable = true,
    		description = NULL_OR_NOT_BLANK_DESCRIPTION)
    @NullOrNotBlankValidation
    @Getter
    @Setter
    @ValidPhoneNumber
    private String dutyPhone;

    /**
     * The person's official job title
     */
    @Schema(nullable = true,
    		description = NULL_OR_NOT_BLANK_DESCRIPTION)
    @Size(max = 255)
    @NullOrNotBlankValidation
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
     * The organizations this person is a member of, this
     * is read-only and cannot be set thru POST, PUT, or JSON PATCH.
     * To change, must go thru the Organization API
     */
    @NonPatchableField
    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Set<UUID> organizationMemberships;

    @JsonIgnore
    @JsonSetter(PersonDto.ORG_MEMBERSHIPS_FIELD)
    public void setOrgMemberships(Set<UUID> orgMemberships) { }  //NOSONAR

    /**
     * The organizations this person is the leader of, this
     * is read-only and cannot be set thru POST, PUT, or JSON PATCH.
     * To change, must go thru the Organization API
     */
    @NonPatchableField
    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Set<UUID> organizationLeaderships;

    @JsonIgnore
    @JsonSetter(PersonDto.ORG_LEADERSHIPS_FIELD)
    public void setOrgLeaderships(Set<UUID> orgLeaderships) {}  //NOSONAR

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

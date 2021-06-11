package mil.tron.commonapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import mil.tron.commonapi.annotation.security.PiiField;
import mil.tron.commonapi.validations.ValidDodId;

import javax.validation.constraints.Email;

/**
 * Represents the parts we care about from a P1 Istio JWT
 */

@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlatformJwtDto {

    @Getter
    @Setter
    private String affiliation;

    @Getter
    @Setter
    private String rank;

    @Getter
    @Setter
    @PiiField
    @Email(regexp = ".+@.+\\..+$", message = "Malformed email address")
    private String email;

    @Getter
    @Setter
    @PiiField
    @ValidDodId
    @JsonProperty("dod_id")
    private String dodId;

    @Getter
    @Setter
    @PiiField
    @JsonProperty("given_name")
    private String givenName;

    @Getter
    @Setter
    @PiiField
    @JsonProperty("family_name")
    private String familyName;
}

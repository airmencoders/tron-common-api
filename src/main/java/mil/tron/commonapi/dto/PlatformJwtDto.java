package mil.tron.commonapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

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
    private String email;

    @Getter
    @Setter
    @JsonProperty("dod_id")
    private String dodId;

    @Getter
    @Setter
    @JsonProperty("given_name")
    private String givenName;

    @Getter
    @Setter
    @JsonProperty("family_name")
    private String familyName;
}

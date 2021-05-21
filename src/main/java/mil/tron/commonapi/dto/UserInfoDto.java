package mil.tron.commonapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.*;
import mil.tron.commonapi.annotation.security.PiiField;

@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserInfoDto {

	@PiiField
	@Getter
	@Setter
	private String dodId;

	@PiiField
	@Getter
	@Setter
	private String givenName;

	@PiiField
	@Getter
	@Setter
	private String familyName;

	@PiiField
	@Getter
	@Setter
	private String name;

	@Getter
	@Setter
	private String preferredUsername;

	@PiiField
	@Getter
	@Setter
	private String email;

	@Getter
	@Setter
	private String organization;
}

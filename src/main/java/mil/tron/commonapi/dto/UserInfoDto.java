package mil.tron.commonapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserInfoDto {

	@Getter
	@Setter
	private String dodId;

	@Getter
	@Setter
	private String givenName;

	@Getter
	@Setter
	private String familyName;

	@Getter
	@Setter
	private String name;

	@Getter
	@Setter
	private String preferredUsername;

	@Getter
	@Setter
	private String email;

	@Getter
	@Setter
	private String organization;
}

package mil.tron.commonapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserInfoDto {
	private String dodId;
	private String givenName;
	private String familyName;
	private String name;
	private String preferredUsername;
	private String email;
	private String organization;
}

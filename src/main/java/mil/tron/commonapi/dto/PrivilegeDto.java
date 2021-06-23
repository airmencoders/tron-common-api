package mil.tron.commonapi.dto;

import javax.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrivilegeDto {
	@Getter
    @Setter
    private Long id;
	
	@Getter
	@Setter
	@NotBlank
	private String name;
}

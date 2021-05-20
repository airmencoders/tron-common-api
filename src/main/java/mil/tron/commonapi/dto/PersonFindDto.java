package mil.tron.commonapi.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mil.tron.commonapi.service.PersonFindType;

@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonFindDto {
	@Getter
	@Setter
	@NotNull
	PersonFindType findType;
	
	@Getter
	@Setter
	@NotBlank
	String value;
}

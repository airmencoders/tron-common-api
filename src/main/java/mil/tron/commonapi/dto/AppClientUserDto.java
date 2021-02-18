package mil.tron.commonapi.dto;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

import javax.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mil.tron.commonapi.entity.Privilege;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppClientUserDto {
    @Getter
    @Setter
    @Builder.Default
    private UUID id = UUID.randomUUID();
	
	@Getter
	@Setter
	private String name;
	
	@Getter
	@Setter
	@Builder.Default
	private List<Privilege> privileges = new ArrayList<>();
}

package mil.tron.commonapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import mil.tron.commonapi.entity.Privilege;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
	@Builder.Default
	private List<Privilege> privileges = new ArrayList<>();

	@Getter
	@Setter
	private String name;

}

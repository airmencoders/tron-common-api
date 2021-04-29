package mil.tron.commonapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.appsource.AppEndpointPriv;

import java.util.*;

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
	@Builder.Default
	private List<String> appClientDeveloperEmails = new ArrayList<>();

	@Getter
	@Setter
	private Set<AppEndpointPriv> appEndpointPrivs = new HashSet<>();

	@Getter
	@Setter
	private String name;

}

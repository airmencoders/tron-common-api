package mil.tron.commonapi.dto.appclient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.appsource.AppEndpointPriv;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
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
	@NotNull
	@NotBlank
	private String name;

}

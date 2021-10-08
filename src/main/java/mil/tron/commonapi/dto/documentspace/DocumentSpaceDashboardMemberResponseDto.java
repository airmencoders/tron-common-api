package mil.tron.commonapi.dto.documentspace;

import java.util.List;
import java.util.UUID;

import javax.validation.constraints.NotNull;

import lombok.Value;

@Value
public class DocumentSpaceDashboardMemberResponseDto {
	@NotNull
	private UUID id;
	
	@NotNull
	private String email;
	
	@NotNull
	private List<DocumentSpacePrivilegeDto> privileges;
}

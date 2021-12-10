package mil.tron.commonapi.dto.documentspace;

import java.util.List;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class DocumentSpaceDashboardMemberRequestDto {
	@NotNull
	@Email(regexp = ".+@.+\\..+$", message = "Malformed email address")
	private String email;
	
	@NotNull
	private List<ExternalDocumentSpacePrivilegeType> privileges;
}

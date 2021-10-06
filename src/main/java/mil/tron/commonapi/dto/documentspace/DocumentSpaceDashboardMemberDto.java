package mil.tron.commonapi.dto.documentspace;

import java.util.List;

import javax.validation.constraints.Email;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mil.tron.commonapi.service.documentspace.DocumentSpacePrivilegeType;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class DocumentSpaceDashboardMemberDto {
	@Email(regexp = ".+@.+\\..+$", message = "Malformed email address")
	private String email;
	
	private List<DocumentSpacePrivilegeType> privileges;
}

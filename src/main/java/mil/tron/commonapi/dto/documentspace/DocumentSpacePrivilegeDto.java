package mil.tron.commonapi.dto.documentspace;

import java.util.UUID;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mil.tron.commonapi.service.documentspace.DocumentSpacePrivilegeType;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class DocumentSpacePrivilegeDto {
    @NotNull
	@Builder.Default
    private UUID id = UUID.randomUUID();
	
    @NotBlank
	private String name;
    
    private DocumentSpacePrivilegeType type;
}

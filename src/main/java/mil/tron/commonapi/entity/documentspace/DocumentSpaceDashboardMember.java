package mil.tron.commonapi.entity.documentspace;

import java.util.UUID;

import lombok.Value;

@Value
public class DocumentSpaceDashboardMember {
	private final UUID id;
	private final String email;
}

package mil.tron.commonapi.entity.documentspace;

import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class DocumentSpaceDashboardMemberPrivilegeRow {
	private final DocumentSpaceDashboardMember dashboardMember;
	private final DocumentSpacePrivilege privilege;
	
	public DocumentSpaceDashboardMemberPrivilegeRow(final UUID id, final String email, final DocumentSpacePrivilege privilege) {
		this.dashboardMember = new DocumentSpaceDashboardMember(id, email);
		this.privilege = privilege;
	}
}

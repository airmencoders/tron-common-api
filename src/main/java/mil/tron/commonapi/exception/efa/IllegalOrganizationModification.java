package mil.tron.commonapi.exception.efa;

import java.util.List;

import lombok.Getter;
import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.service.fieldauth.EntityFieldAuthResponse;
import mil.tron.commonapi.service.fieldauth.EntityFieldAuthType;

public class IllegalOrganizationModification extends IllegalModificationBaseException {
	@Getter
	private final transient Organization data;
	
	public IllegalOrganizationModification(EntityFieldAuthResponse<Organization> efaResponse) {
		super(efaResponse.getDeniedFields());
		this.data = efaResponse.getModifiedEntity();
	}
	
	public IllegalOrganizationModification(Organization data, List<String> deniedFields) {
		super(deniedFields);
		this.data = data;
	}

	@Override
	public EntityFieldAuthType getEfaType() {
		return EntityFieldAuthType.ORGANIZATION;
	}
}

package mil.tron.commonapi.exception.efa;

import java.util.List;

import lombok.Getter;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.service.fieldauth.EntityFieldAuthResponse;
import mil.tron.commonapi.service.fieldauth.EntityFieldAuthType;

public class IllegalPersonModification extends IllegalModificationBaseException {
	@Getter
	private final transient Person data;
	
	public IllegalPersonModification(EntityFieldAuthResponse<Person> efaResponse) {
		super(efaResponse.getDeniedFields());
		this.data = efaResponse.getModifiedEntity();
	}
	
	public IllegalPersonModification(Person data, List<String> deniedFields) {
		super(deniedFields);
		this.data = data;
	}

	@Override
	public EntityFieldAuthType getEfaType() {
		return EntityFieldAuthType.PERSON;
	}
}

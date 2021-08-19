package mil.tron.commonapi.exception.efa;

import java.util.List;

import lombok.Getter;
import mil.tron.commonapi.service.fieldauth.EntityFieldAuthType;

public abstract class IllegalModificationBaseException extends RuntimeException {
	@Getter
	private final List<String> deniedFields;
	
	protected IllegalModificationBaseException(List<String> deniedFields) {
		super(createMessage(deniedFields));
		this.deniedFields = deniedFields;
	}
	
	public abstract Object getData();
	public abstract EntityFieldAuthType getEfaType();
	
	protected static String createMessage(List<String> deniedFields) {
		return String.format("Entity partially updated with the following fields denied: %s", String.join(", ", deniedFields));
	}
}

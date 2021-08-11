package mil.tron.commonapi.service.utility;

import org.springframework.web.bind.MethodArgumentNotValidException;

public interface ValidatorService {
	/**
	 * Validates the properties of an object.
	 * 
	 * @param <T> type of the object to validate
	 * @param objectToValidate object to run validation against
	 * @param objectClass the class of the object to validate
	 * @return true if validation passed
	 * @throws MethodArgumentNotValidException throws if validation failed
	 */
	<T> boolean isValid(T objectToValidate, Class<T> objectClass) throws MethodArgumentNotValidException;
}

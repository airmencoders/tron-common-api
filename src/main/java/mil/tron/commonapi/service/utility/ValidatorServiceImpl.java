package mil.tron.commonapi.service.utility;


import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Validator;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ValidatorServiceImpl implements ValidatorService {
	private final Validator validator;
	
	public ValidatorServiceImpl(Validator validator) {
		this.validator = validator;
	}

	/**
	 * Uses {@link org.springframework.validation.Validator} to validate object properties.
	 */
	@Override
	public <T> boolean isValid(T objectToValidate, Class<T> objectClass) throws MethodArgumentNotValidException {
		BeanPropertyBindingResult errors = new BeanPropertyBindingResult(objectToValidate, objectClass.getSimpleName());
		validator.validate(objectToValidate, errors);
		
		MethodParameter thisMethodParameter;
		try {
			thisMethodParameter = new MethodParameter(this.getClass().getMethod("isValid", Object.class, Class.class), 0);
		} catch (NoSuchMethodException | SecurityException ex) {
			log.error("Error occurred trying to get method for isValid");
			throw new ResponseStatusException(
					HttpStatus.INTERNAL_SERVER_ERROR,
					String.format("There was an internal error validating object='%s'", objectClass.getSimpleName()));
		}
		
		if (errors.hasErrors()) {
			throw new MethodArgumentNotValidException(thisMethodParameter, errors);
		}
		
		return true;
	}
}

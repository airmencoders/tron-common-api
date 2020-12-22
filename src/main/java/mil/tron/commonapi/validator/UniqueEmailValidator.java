package mil.tron.commonapi.validator;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import mil.tron.commonapi.annotation.UniqueEmailConstraint;
import mil.tron.commonapi.repository.PersonRepository;

public class UniqueEmailValidator implements ConstraintValidator<UniqueEmailConstraint, String> {
	private PersonRepository personRepository;
	
	public UniqueEmailValidator(PersonRepository personRepository) {
		this.personRepository = personRepository;
	}
	
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
//		return personRepository.findByEmailAsLower(value.toLowerCase()).isEmpty();
		return personRepository.findByEmailIgnoreCase(value).isEmpty();
	}

}

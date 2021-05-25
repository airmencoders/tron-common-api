package mil.tron.commonapi.validations;

import mil.tron.commonapi.dto.PersonDto;
import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EmailValidationTest {

    @Test
    void testEmailValidation() {

        PersonDto dto = PersonDto.builder()
                .email("test@test.com")
                .build();

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();

        Set<ConstraintViolation<PersonDto>> violations = validator.validate(dto);

        assertTrue(violations.isEmpty());

        dto.setEmail("test@test");
        violations = validator.validate(dto);
        assertFalse(violations.isEmpty());

        dto.setEmail("test@test.us.af.mil");
        violations = validator.validate(dto);
        assertTrue(violations.isEmpty());

        dto.setEmail("test@test.somedomain");
        violations = validator.validate(dto);
        assertTrue(violations.isEmpty());

        dto.setEmail("@test.somedomain");
        violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
    }
}

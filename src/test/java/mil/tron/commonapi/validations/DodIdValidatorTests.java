package mil.tron.commonapi.validations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Yes this is borrowed from Puckboard
 */

public class DodIdValidatorTests {
    private DodIdValidator validator = new DodIdValidator();

    @Test
    public void shouldReturnTrueOnValidDodId(){
        assertTrue(validator.isValid("123456789", null));
    }

    @Test
    public void shouldReturnFalseOnInvalidDodId(){
        assertFalse(validator.isValid("1234", null));
        assertFalse(validator.isValid("12345678901", null));
    }

}

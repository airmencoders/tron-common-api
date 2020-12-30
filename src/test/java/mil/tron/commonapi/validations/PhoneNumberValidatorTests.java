package mil.tron.commonapi.validations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Yes this is borrowed from Puckboard
 */

public class PhoneNumberValidatorTests {

    private PhoneNumberValidator validator = new PhoneNumberValidator();

    @Test
    public void shouldReturnTrueWhenValidPhoneNumber(){
        assertTrue(validator.isValid("(808) 867-5309", null));
    }

    @Test
    public void shouldReturnFalseWhenInvalidPhoneNumber(){
        assertFalse(validator.isValid("(111) 167-1309", null));
        assertFalse(validator.isValid("Phone Number", null));
        assertFalse(validator.isValid("000-000-0000", null));
    }

}

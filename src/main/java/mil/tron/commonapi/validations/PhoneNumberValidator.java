package mil.tron.commonapi.validations;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * Borrowed from Puckboard
 */

public class PhoneNumberValidator implements ConstraintValidator<ValidPhoneNumber, String> {
    public void initialize(ValidPhoneNumber constraint) {
        // default implementation ignored
    }

    public boolean isValid(String phoneNumber, ConstraintValidatorContext context) {

        String regex = "^(?:\\([2-9]\\d{2}\\) ?|[2-9]\\d{2}(?:-?| ?))[2-9]\\d{2}[- ]?\\d{4}$";//NOSONAR
        Pattern p = Pattern.compile(regex);
        return phoneNumber == null || phoneNumber.isEmpty() || p.matcher(phoneNumber).matches();
    }
}
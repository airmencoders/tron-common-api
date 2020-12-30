package mil.tron.commonapi.validations;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * Borrowed from Puckboard... with some mods since ours is a String type
 */

public class DodIdValidator implements ConstraintValidator<ValidDodId, String> {

    @Override
    public void initialize(ValidDodId constraint) {
        // default implementation ignored
    }

    public boolean isValid(String dodId, ConstraintValidatorContext context) {//NOSONAR

        if(dodId == null) return true;
        String regex = "^\\d{5,10}$";
        Pattern p = Pattern.compile(regex);
        return p.matcher(dodId).matches();
    }
}

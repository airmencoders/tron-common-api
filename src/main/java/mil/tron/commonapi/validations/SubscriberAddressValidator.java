package mil.tron.commonapi.validations;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class SubscriberAddressValidator implements ConstraintValidator<ValidSubscriberAddress, String> {

    @Override
    public void initialize(ValidSubscriberAddress constraint) {
        // default implementation ignored
    }

    /**
     * Only allow cluster local addresses or localhost
     */
    public boolean isValid(String address, ConstraintValidatorContext context) {//NOSONAR
        String regex = "^http://.+?\\..+?\\.svc.cluster.local/|^http://localhost:\\d+/";
        Pattern p = Pattern.compile(regex);
        return p.matcher(address).find();
    }
}

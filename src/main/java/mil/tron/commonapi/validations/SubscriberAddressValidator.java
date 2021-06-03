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
     * Disallow setting Common API itself as a subscriber (cannot have tron-common-api anywhere in the URL)
     */
    public boolean isValid(String address, ConstraintValidatorContext context) {//NOSONAR
        if (address == null) return true;

        String regex = "^http://(?!tron-common-api).+?\\.(?!tron-common-api).+?\\.svc.cluster.local/|^http://localhost:\\d+/";
        Pattern p = Pattern.compile(regex);
        return p.matcher(address).find();
    }
}

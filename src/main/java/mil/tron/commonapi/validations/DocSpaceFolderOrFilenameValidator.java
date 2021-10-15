package mil.tron.commonapi.validations;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class DocSpaceFolderOrFilenameValidator implements ConstraintValidator<ValidDocSpaceFolderOrFilename, String> {

    @Override
    public void initialize(ValidDocSpaceFolderOrFilename constraint) {
        // default implementation ignored
    }

    public boolean isValid(String filename, ConstraintValidatorContext context) {//NOSONAR

        if (filename == null) return false;
        if (filename.isBlank()) return false;
        String regex = "^[A-Za-z0-9.-]+$";
        Pattern p = Pattern.compile(regex);
        return p.matcher(filename).matches();
    }
}

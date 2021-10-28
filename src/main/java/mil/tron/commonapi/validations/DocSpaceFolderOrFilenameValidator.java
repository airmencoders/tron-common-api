package mil.tron.commonapi.validations;

import org.springframework.util.StringUtils;

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

        // loosely follows https://help.dropbox.com/files-folders/sort-preview/file-names
        // while still disallowing special URL-ish characters (&+%?, etc)
        Pattern nonRestrictedCharsRegex = Pattern.compile("[A-Za-z0-9-_()\\s.]+");
        Pattern noWhiteSpaceBeforeExtension = Pattern.compile("\\s\\.");
        Pattern noWhiteSpaceAfterExtension = Pattern.compile("\\.\\s");
        return nonRestrictedCharsRegex.matcher(filename).matches()
                && StringUtils.countOccurrencesOf(filename, ".") <= 1
                && !noWhiteSpaceBeforeExtension.matcher(filename).find()
                && !noWhiteSpaceAfterExtension.matcher(filename).find();
    }
}

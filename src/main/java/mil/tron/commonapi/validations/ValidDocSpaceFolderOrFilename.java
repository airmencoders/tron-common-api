package mil.tron.commonapi.validations;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates a doc space filename
*/
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DocSpaceFolderOrFilenameValidator.class)
public @interface ValidDocSpaceFolderOrFilename {
    String message() default "does not appear to be a valid doc space filename or folder name";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

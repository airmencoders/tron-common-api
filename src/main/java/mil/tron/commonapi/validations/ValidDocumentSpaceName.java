package mil.tron.commonapi.validations;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DocumentSpaceNameValidator.class)
public @interface ValidDocumentSpaceName {
    String message() default "does not appear to be a valid minio bucket name";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

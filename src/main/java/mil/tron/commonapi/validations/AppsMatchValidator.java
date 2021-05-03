package mil.tron.commonapi.validations;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import org.springframework.beans.BeanWrapperImpl;

import mil.tron.commonapi.entity.appsource.App;

public class AppsMatchValidator implements ConstraintValidator<AppsMatch, Object> {

    private String field;
    private String fieldMatch;
    private boolean invert;

    public void initialize(AppsMatch constraintAnnotation) {
        this.field = constraintAnnotation.field();
        this.fieldMatch = constraintAnnotation.fieldMatch();
        this.invert = constraintAnnotation.invert();
    }

    public boolean isValid(Object value, ConstraintValidatorContext context) {//NOSONAR
        if(invert) {
            return !resolve(value);
        }
        return resolve(value);
        
    }

    private boolean resolve(Object value) {
        Object fieldValue = new BeanWrapperImpl(value)
          .getPropertyValue(field);
        Object fieldMatchValue = new BeanWrapperImpl(value)
          .getPropertyValue(fieldMatch);
        if (fieldValue != null) {                            
            if(fieldMatchValue == null) {
                return false;
            // If both fields are of type app, we should compare by Id instead
            } else  if(fieldValue instanceof App && fieldMatchValue instanceof App) {
                return ((App) fieldValue).getId().equals(((App) fieldMatchValue).getId());
            }
            return fieldValue.equals(fieldMatchValue);
        } else {
            return fieldMatchValue == null;
        }
    }
    
}

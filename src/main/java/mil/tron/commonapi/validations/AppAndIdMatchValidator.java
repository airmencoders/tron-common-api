package mil.tron.commonapi.validations;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import mil.tron.commonapi.dto.appsource.AppSourceDetailsDto;

public class AppAndIdMatchValidator implements ConstraintValidator<AppAndIdMatch, Object> {

    public void initialize(AppAndIdMatch constraintAnnotation) { 
        // default implementation ignored
     }

    public boolean isValid(Object value, ConstraintValidatorContext context) { //NOSONAR
        if(value instanceof AppSourceDetailsDto) {
            AppSourceDetailsDto dto = (AppSourceDetailsDto) value;
            if(dto.getAppClients().isEmpty() || dto.getId() == null) {
                return true;
            }
            return !dto.getAppClients().stream().anyMatch(item -> dto.getId().equals(item.getAppClientUser()));
        }
        return true;
    }
    
}

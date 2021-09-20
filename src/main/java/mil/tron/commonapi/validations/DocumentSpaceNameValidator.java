package mil.tron.commonapi.validations;

import mil.tron.commonapi.service.documentspace.DocumentSpaceServiceImpl;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;


public class DocumentSpaceNameValidator implements ConstraintValidator<ValidDocumentSpaceName, String>  {

    @Override
    public boolean isValid(String s, ConstraintValidatorContext constraintValidatorContext) {//NOSONAR
        return DocumentSpaceServiceImpl.verifySpaceNameValid(s);
    }
}

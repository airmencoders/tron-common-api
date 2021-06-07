package mil.tron.commonapi.validations;

import java.util.List;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import mil.tron.commonapi.repository.filter.FilterCondition;
import mil.tron.commonapi.repository.filter.QueryOperator;

public class FilterConditionValidator implements ConstraintValidator<FilterConditionValidation, FilterCondition> {
	@Override
	public void initialize(FilterConditionValidation annotation) {
		/**
		 * No initialization necessary
		 */
	}

	@Override
	public boolean isValid(FilterCondition filterCondition, ConstraintValidatorContext ctx) {
		if (filterCondition == null) {
			return true;
		}

		boolean isValid = true;

		String value = filterCondition.getValue();
		List<String> values = filterCondition.getValues();

		// Only one of value or values must be set
		if ((value == null && values == null) || (value != null && values != null)) {
			ctx.disableDefaultConstraintViolation();
			ctx.buildConstraintViolationWithTemplate(
					"Only one field of [value] or [values] may be set depending on the QueryOperator used")
					.addPropertyNode("value").addConstraintViolation();

			ctx.buildConstraintViolationWithTemplate(
					"Only one field of [value] or [values] may be set depending on the QueryOperator used")
					.addPropertyNode("values").addConstraintViolation();

			isValid = false;
		}
		
		// When the operator is anything but IN, value must be set
		if (!filterCondition.getOperator().equals(QueryOperator.IN) && value == null) {
			ctx.disableDefaultConstraintViolation();
			ctx.buildConstraintViolationWithTemplate("Field [value] must be set")
					.addPropertyNode("value").addConstraintViolation();

			isValid = false;
		}

		// When the operator is IN, values must be set
		if (filterCondition.getOperator().equals(QueryOperator.IN) && (values == null || values.isEmpty())) {
			ctx.disableDefaultConstraintViolation();
			ctx.buildConstraintViolationWithTemplate("Field [values] must be set when using QueryOperator.IN")
					.addPropertyNode("values").addConstraintViolation();

			isValid = false;
		}

		return isValid;
	}

}
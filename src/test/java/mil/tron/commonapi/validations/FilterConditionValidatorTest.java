package mil.tron.commonapi.validations;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import mil.tron.commonapi.repository.filter.FilterCondition;
import mil.tron.commonapi.repository.filter.QueryOperator;


class FilterConditionValidatorTest {
	private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }
    
    @Test
    void testSuccess() {
    	FilterCondition condition = FilterCondition.builder()
    									.operator(QueryOperator.EQUALS)
    									.value("test")
    									.build();
    	
    	Set<ConstraintViolation<FilterCondition>> violations = validator.validate(condition);
        assertThat(violations).size().isEqualTo(0);
        
        condition = FilterCondition.builder()
				.operator(QueryOperator.IN)
				.values(List.of("test", "list"))
				.build();
        
        violations = validator.validate(condition);
        assertThat(violations).size().isEqualTo(0);
    }
    
    @Test
    void testFailNoValueSet() {
    	FilterCondition condition = FilterCondition.builder()
				.operator(QueryOperator.EQUALS)
				.build();

		Set<ConstraintViolation<FilterCondition>> violations = validator.validate(condition);
		assertThat(violations).size().isEqualTo(3);
    }
    
    @Test
    void testFailValueNotSet() {
    	FilterCondition condition = FilterCondition.builder()
				.operator(QueryOperator.EQUALS)
				.values(List.of("test", "list"))
				.build();

		Set<ConstraintViolation<FilterCondition>> violations = validator.validate(condition);
		assertThat(violations).size().isEqualTo(1);
    }
    
    @Test
    void testFailValuesNotSet() {
    	FilterCondition condition = FilterCondition.builder()
				.operator(QueryOperator.IN)
				.value("test")
				.build();

		Set<ConstraintViolation<FilterCondition>> violations = validator.validate(condition);
		assertThat(violations).size().isEqualTo(1);
    }
    
}

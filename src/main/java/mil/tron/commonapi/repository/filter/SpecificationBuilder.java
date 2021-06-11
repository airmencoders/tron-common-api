package mil.tron.commonapi.repository.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.persistence.criteria.Join;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;

import org.springframework.data.jpa.domain.Specification;

import mil.tron.commonapi.exception.BadRequestException;

import static org.springframework.data.jpa.domain.Specification.where;

public class SpecificationBuilder {
	private SpecificationBuilder() {
	}

	/**
	 * Creates individual Specification based on a condition
	 * 
	 * @param <T>           The entity Type
	 * @param field         the name of the field
	 * @param joinAttribute the name of the attribute to join if it exists, null if
	 *                      not
	 * @param input         the {@code FilterCondition}
	 * @return the Specification created on the {@code FilterCondition}
	 */
	private static <T> Specification<T> createSpecification(String field, String joinAttribute, FilterCondition input) {
		switch (input.getOperator()) {
			case EQUALS:
				return (root, query, criteriaBuilder) -> {
					query.distinct(true);
					Path<Object> dbPathObj = getDbPathObject(root, joinAttribute, field);
					
					checkOperatorSupportsInput(input.getOperator(), dbPathObj.getJavaType(), field);
					
					return criteriaBuilder.equal(dbPathObj, castToRequiredType(dbPathObj.getJavaType(), field, input.getValue()));
				};
	
			case NOT_EQUALS:
				return (root, query, criteriaBuilder) -> {
					query.distinct(true);
					Path<Object> dbPathObj = getDbPathObject(root, joinAttribute, field);
					
					checkOperatorSupportsInput(input.getOperator(), dbPathObj.getJavaType(), field);
					
					return criteriaBuilder.notEqual(dbPathObj,
							castToRequiredType(dbPathObj.getJavaType(), field, input.getValue()));
				};
	
			case GREATER_THAN:
				return (root, query, criteriaBuilder) -> {
					query.distinct(true);
					Path<Number> dbPathObj = getDbPathObject(root, joinAttribute, field);
	
					checkOperatorSupportsInput(input.getOperator(), dbPathObj.getJavaType(), field);
	
					return criteriaBuilder.gt(dbPathObj,
							(Number) castToRequiredType(dbPathObj.getJavaType(), field, input.getValue()));
				};
	
			case LESS_THAN:
				return (root, query, criteriaBuilder) -> {
					query.distinct(true);
					Path<Number> dbPathObj = getDbPathObject(root, joinAttribute, field);
	
					checkOperatorSupportsInput(input.getOperator(), dbPathObj.getJavaType(), field);
	
					return criteriaBuilder.lt(dbPathObj,
							(Number) castToRequiredType(dbPathObj.getJavaType(), field, input.getValue()));
				};
	
			case LIKE:
				return (root, query, criteriaBuilder) -> {
					query.distinct(true);
					Path<String> dbPathObj = getDbPathObject(root, joinAttribute, field);
	
					checkOperatorSupportsInput(input.getOperator(), dbPathObj.getJavaType(), field);
	
					return criteriaBuilder.like(dbPathObj, "%" + castToRequiredType(dbPathObj.getJavaType(), field, input.getValue()) + "%");
				};
	
			case NOT_LIKE:
				return (root, query, criteriaBuilder) -> {
					query.distinct(true);
					Path<String> dbPathObj = getDbPathObject(root, joinAttribute, field);
	
					checkOperatorSupportsInput(input.getOperator(), dbPathObj.getJavaType(), field);
	
					return criteriaBuilder.notLike(dbPathObj, "%" + castToRequiredType(dbPathObj.getJavaType(), field, input.getValue()) + "%");
				};
	
			case IN:
				return (root, query, criteriaBuilder) -> {
					query.distinct(true);
					Path<Object> dbPathObj = getDbPathObject(root, joinAttribute, field);
					
					checkOperatorSupportsInput(input.getOperator(), dbPathObj.getJavaType(), field);
					
					return criteriaBuilder.in(dbPathObj)
							.value(castToRequiredType(dbPathObj.getJavaType(), field, input.getValues()));
				};
	
			case STARTS_WITH:
				return (root, query, criteriaBuilder) -> {
					query.distinct(true);
					Path<String> dbPathObj = getDbPathObject(root, joinAttribute, field);
	
					checkOperatorSupportsInput(input.getOperator(), dbPathObj.getJavaType(), field);
	
					return criteriaBuilder.like(dbPathObj, castToRequiredType(dbPathObj.getJavaType(), field, input.getValue()) + "%");
				};
	
			case ENDS_WITH:
				return (root, query, criteriaBuilder) -> {
					query.distinct(true);
					Path<String> dbPathObj = getDbPathObject(root, joinAttribute, field);
	
					checkOperatorSupportsInput(input.getOperator(), dbPathObj.getJavaType(), field);
	
					return criteriaBuilder.like(dbPathObj, "%" + castToRequiredType(dbPathObj.getJavaType(), field, input.getValue()));
				};
	
			default:
				throw new BadRequestException("Operation not supported");
		}
	}

	/**
	 * Creates the path to the attribute.
	 * 
	 * @param <T>           type of the attribute
	 * @param root          the Root object
	 * @param joinAttribute the attribute to join onto root, null if none
	 * @param field         the name of the attribute field
	 * @return path of the attribute
	 */
	private static <T> Path<T> getDbPathObject(Root<?> root, String joinAttribute, String field) {
		Path<T> dbPathValue = null;

		if (joinAttribute != null && !joinAttribute.isBlank()) {
			try {
				Join<Object, Object> joined = root.join(joinAttribute);
				dbPathValue = joined.get(field);
			} catch (IllegalArgumentException ex) {
				throw new BadRequestException(
						String.format("Field [%s] with Join Attribute [%s] does not exist", field, joinAttribute));
			}
		} else {
			try {
				dbPathValue = root.get(field);
			} catch (IllegalArgumentException ex) {
				throw new BadRequestException(String.format("Field [%s] does not exist", field));
			}
		}
		
		return dbPathValue;
	}

	/**
	 * Checks to make sure the operator supports the input class.
	 * 
	 * @param operator the operator to check
	 * @param inputTypeToCheck the type to check against
	 * @return true if the operator supports the class or the operator has no constraints, false otherwise
	 */
	private static boolean validateOperatorSupportsInputType(QueryOperator operator, Class<?> inputTypeToCheck) {
		switch (operator) {
			case GREATER_THAN:
			case LESS_THAN:
				return Number.class.isAssignableFrom(inputTypeToCheck);
	
			case LIKE:
			case NOT_LIKE:
			case STARTS_WITH:
			case ENDS_WITH:
				return String.class.isAssignableFrom(inputTypeToCheck);
	
			default:
				return true;
		}
	}
	
	/**
	 * Checks that the operator supports the input class
	 * 
	 * @param operator the operator to check
	 * @param inputTypeToCheck the type to check against
	 * @param fieldName the name of the field
	 * 
	 * @throws BadRequestException if operator does not support input class
	 */
	private static void checkOperatorSupportsInput(QueryOperator operator, Class<?> inputTypeToCheck, String fieldName) {
		if (!validateOperatorSupportsInputType(operator, inputTypeToCheck)) {
			throw new BadRequestException(String.format("Field [%s] is not supported with this operator [%s]", fieldName, operator));
		}
	}

	/**
	 * Converts a string value to associated type.
	 * 
	 * @param fieldType the class of the field
	 * @param value     the value
	 * @return object casted to {@code fieldType} if possible, else just returns
	 *         back the string {@code value}
	 *         
	 * @throws BadRequestException could not parse value into its associated database type
	 */
	@SuppressWarnings("unchecked")
	private static Object castToRequiredType(Class<?> fieldType, String fieldName, String value) {
		try {
			if (fieldType.isAssignableFrom(Double.class)) {
				return Double.valueOf(value);
			} else if (fieldType.isAssignableFrom(Integer.class)) {
				return Integer.valueOf(value);
			} else if (Enum.class.isAssignableFrom(fieldType)) {
				return Enum.valueOf(fieldType.asSubclass(Enum.class), value); //NOSONAR
			} else if (UUID.class.isAssignableFrom(fieldType)) {
				return UUID.fromString(value);
			}
		} catch (IllegalArgumentException ex) {
			throw new BadRequestException(String.format("Could not parse Value [%s] for Field [%s]", value, fieldName));
		}
		
		return value;
	}

	/**
	 * Converts a list of string values to associated type.
	 * 
	 * @param fieldType the class of the field
	 * @param values    the list of values
	 * @return list containing objects casted to {@code fieldType} if possible, else
	 *         just returns back the list of strings {@code value}
	 */
	private static Object castToRequiredType(Class<?> fieldType, String fieldName, List<String> values) {
		List<Object> lists = new ArrayList<>();
		for (String value : values) {
			lists.add(castToRequiredType(fieldType, fieldName, value));
		}
		return lists;
	}

	/**
	 * Creates the Specification given the list of filter criteria.
	 * 
	 * @param <T>    The type of the Entity for the Specification
	 * @param filter the list of filter criteria
	 * @return the Specification created from {@code filter}
	 */
	public static <T> Specification<T> getSpecificationFromFilters(List<FilterCriteria> filter) {
		Specification<T> specification = where(null);

		for (FilterCriteria input : filter) {
			Specification<T> nestedSpec = where(null);

			if (input.getRelationType() == RelationType.OR) {
				for (FilterCondition condition : input.getConditions()) {
					nestedSpec = nestedSpec
							.or(createSpecification(input.getField(), input.getJoinAttribute(), condition));
				}

			} else {
				for (FilterCondition condition : input.getConditions()) {
					nestedSpec = nestedSpec
							.and(createSpecification(input.getField(), input.getJoinAttribute(), condition));
				}
			}

			specification = specification.and(nestedSpec);
		}

		return specification;
	}
}

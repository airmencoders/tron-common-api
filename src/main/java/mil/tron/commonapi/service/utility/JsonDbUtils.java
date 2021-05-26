package mil.tron.commonapi.service.utility;

import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.exception.scratch.InvalidDataTypeException;
import org.apache.commons.validator.routines.EmailValidator;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Static class with helpers for the Json DB methods in the Scratch Storage service
 */

public class JsonDbUtils {

    private static final String FIELD_VALIDATION_ERROR = "Field - %s - was supposed to be a %s but wasn't";
    private static final String TYPE_NUMBER = "number";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_EMAIL = "email";
    private static final String TYPE_UUID = "uuid";
    private static final String TYPE_BOOLEAN = "boolean";

    private JsonDbUtils() {
        throw new IllegalStateException("Private Constructor cannot be called");
    }

    /**
     * Helper method to set a default field value for a field that was omitted in a Json Request, the default
     * value is determined by the data type set in the users "schema"
     *
     * @param fieldName  name of the field we're checking
     * @param fieldValue value specifying the data type of this field, as specificied in the users "schema" they defined
     * @return the default value for a given field and its type
     */
    public static Object defaultValueForField(String fieldName, String fieldValue) {

        // if a schema field has an exclamation in its data type - then its required field
        if (fieldValue.endsWith("!")) {
            throw new InvalidDataTypeException("Field - " + fieldName + " - was specified as required, but was not given");
        }

        if (fieldValue.contains(TYPE_STRING)) {
            return "";
        }
        if (fieldValue.contains(TYPE_EMAIL)) {
            return "";
        }
        if (fieldValue.contains(TYPE_NUMBER)) {
            return 0;
        }
        if (fieldValue.contains(TYPE_BOOLEAN)) {
            return false;
        }
        if (fieldValue.contains(TYPE_UUID)) {
            return UUID.randomUUID();
        }

        throw new InvalidDataTypeException("Invalid type specified for schema field - " + fieldValue);
    }

    /**
     * Helper method to check if a given field name in a json blob's value matches the data type of the
     * schema provided - if not we throw an exception
     *
     * @param fieldName       the name of the field being checked
     * @param schemaType      the type of data this field is supposed to be (as defined in the table_schema key)
     * @param fieldValue      the value sent by the http request that we're going to check for proper type (and other things)
     * @param fieldIsUnique   true if this field is marked as a unique column (its value should be unique)
     * @param blob            the json value (the entire table) of json data used for uniqueness checks if needed
     * @param updateOperation boolean whether we're doing an update or not (disables the unique checks)
     */
    public static void validateField(String fieldName,
                               String schemaType,
                               JsonNode fieldValue,
                               boolean fieldIsUnique,
                               DocumentContext blob,
                               boolean updateOperation) {

        if (schemaType.contains(TYPE_STRING) && !fieldValue.isTextual()) {
            throw new InvalidDataTypeException(String.format(FIELD_VALIDATION_ERROR, fieldName, TYPE_STRING));
        }
        if (schemaType.contains(TYPE_EMAIL) && !fieldValue.isTextual()
                && EmailValidator.getInstance().isValid(fieldValue.asText())) {
            throw new InvalidDataTypeException(String.format(FIELD_VALIDATION_ERROR, fieldName, TYPE_EMAIL));
        }
        if (schemaType.contains(TYPE_NUMBER) && !fieldValue.isNumber()) {
            throw new InvalidDataTypeException(String.format(FIELD_VALIDATION_ERROR, fieldName, TYPE_NUMBER));
        }
        if (schemaType.contains(TYPE_BOOLEAN) && !fieldValue.isBoolean()) {
            throw new InvalidDataTypeException(String.format(FIELD_VALIDATION_ERROR, fieldName, TYPE_BOOLEAN));
        }
        if (schemaType.contains(TYPE_UUID) && !fieldValue.isTextual()
                && !fieldValue
                .asText()
                .matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
            throw new InvalidDataTypeException(String.format(FIELD_VALIDATION_ERROR, fieldName, TYPE_UUID));
        }

        // do the unique checks (if applicable)
        if (fieldIsUnique && !updateOperation) {
            String jsonPath = "$[?(@." + fieldName + " == '" + fieldValue.asText() + "')]";
            List<Map<String, Object>> elems = JsonPath.read(blob.jsonString(), jsonPath);
            if (fieldValue.asText() != null && !fieldValue.asText().isBlank() && elems.size() != 0) {
                throw new ResourceAlreadyExistsException("Field " + fieldName + " violated uniqueness");
            }
        }
    }


}

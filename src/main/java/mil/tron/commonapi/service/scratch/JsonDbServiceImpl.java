package mil.tron.commonapi.service.scratch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import mil.tron.commonapi.entity.scratch.ScratchStorageEntry;
import mil.tron.commonapi.exception.InvalidFieldValueException;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.exception.scratch.InvalidDataTypeException;
import mil.tron.commonapi.exception.scratch.InvalidJsonPathQueryException;
import mil.tron.commonapi.repository.scratch.ScratchStorageRepository;
import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class JsonDbServiceImpl implements JsonDbService {
    private ScratchStorageRepository repository;

    private static final String STRING_TYPE = "string";
    private static final String UUID_TYPE = "uuid";
    private static final String EMAIL_TYPE = "email";
    private static final String NUMBER_TYPE = "number";
    private static final String BOOLEAN_TYPE = "boolean";
    private static final String UNIQUE_IDENTIFIER = "*";
    private static final String REQUIRED_IDENTIFIER = "!";
    private static final String ID_FIELD_NAME = "id";
    private static final String UUID_REGEX = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Configuration configuration = Configuration.builder()
            .jsonProvider(new JacksonJsonNodeJsonProvider())
            .mappingProvider(new JacksonMappingProvider())
            .build();

    public JsonDbServiceImpl(ScratchStorageRepository repository) {
        this.repository = repository;
    }

    //
    //
    // methods to treat scratch space like a json db

    /**
     * Helper method that takes a JsonNode tree representing the Json Schema blob for a give
     * key/table from the scratch space... if it's invalid then we throw an exception.
     *
     * A valid schema must have fields with values that are strings/text value.
     * A valid schema must also have an ID field named ID and its type must be UUID.
     * @param schemaBlob schema data from database
     */
    public void validateSchema(JsonNode schemaBlob) {
        boolean foundIdField = false;
        for (String field : Lists.newArrayList(schemaBlob.fieldNames())) {
            if (!schemaBlob.get(field).isTextual()) {
                throw new InvalidFieldValueException("Schema appears to be invalid");
            }
            if (field.equals(ID_FIELD_NAME)
                    && schemaBlob.get(field).textValue().equals(UUID_TYPE)) foundIdField = true;
        }

        if (!foundIdField) {
            throw new InvalidFieldValueException("Schema appears to be invalid - No ID Field found or had the wrong type");
        }
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
        if (fieldValue.contains(REQUIRED_IDENTIFIER)) {
            throw new InvalidDataTypeException("Field - " + fieldName + " - was specified as required, but was not given");
        }

        if (fieldValue.contains(STRING_TYPE)) {
            return "";
        }
        if (fieldValue.contains(EMAIL_TYPE)) {
            return "";
        }
        if (fieldValue.contains(NUMBER_TYPE)) {
            return 0;
        }
        if (fieldValue.contains(BOOLEAN_TYPE)) {
            return false;
        }
        if (fieldValue.contains(UUID_TYPE)) {
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

        if (schemaType.contains(STRING_TYPE)) {
            if (!fieldValue.isTextual()) {
                throw new InvalidDataTypeException("Field - " + fieldName + " - was supposed to be a string but wasnt");
            }
        }
        if (schemaType.contains(EMAIL_TYPE)) {
            if (!fieldValue.isTextual()
                    && EmailValidator.getInstance().isValid(fieldValue.asText())) {
                throw new InvalidDataTypeException("Field - " + fieldName + " - was supposed to be an email but wasnt");
            }
        }
        if (schemaType.contains(NUMBER_TYPE)) {
            if (!fieldValue.isNumber()) {
                throw new InvalidDataTypeException("Field - " + fieldName + " - was supposed to be a number but wasnt");
            }
        }
        if (schemaType.contains(BOOLEAN_TYPE)) {
            if (!fieldValue.isBoolean()) {
                throw new InvalidDataTypeException("Field - " + fieldName + " - was supposed to be a boolean but wasnt");
            }
        }
        if (schemaType.contains(UUID_TYPE)) {
            if (!fieldValue.isTextual()
                    // make sure we have a valid UUID format
                    && !fieldValue
                    .asText()
                    .matches(UUID_REGEX)) {

                throw new InvalidDataTypeException("Field - " + fieldName + " - was supposed to be a uuid but wasnt");
            }
        }

        // do any unique checks
        if (fieldIsUnique && !updateOperation) {
            String jsonPath = "$[?(@." + fieldName + " == '" + fieldValue.asText() + "')]";
            List<Map<String, Object>> elems = JsonPath.read(blob.jsonString(), jsonPath);
            if (fieldValue.asText() != null && !fieldValue.asText().isBlank() && elems.size() != 0) {
                throw new ResourceAlreadyExistsException("Field " + fieldName + " violated uniqueness");
            }
        }
    }

    /**
     * Helper function to reference a user-defined "schema" for a given key-value pair in order to help validate an incoming
     * json value being assigned to a "table" (key name) when treating scratch-storage space like a JSON db.  The whole
     * point of validating is so we can keep some real-database-like consistency when modifying it
     *
     * @param appId           UUID of the scratch storage app
     * @param tableName       the "table" name - (the key name)
     * @param json            blob of json we're attempting to validate against the schema
     * @param cxt             the JsonPath DocumentContext that is the parsed json of the table
     * @param updateOperation boolean whether we're doing an update operation or not
     * @return the (possibly modified) blob of json (modified if it had missing fields when compared to the schema).
     */
    private Object validateEntityValue(UUID appId,
                                       String tableName,
                                       Object json,
                                       DocumentContext cxt,
                                       boolean updateOperation) {

        ScratchStorageEntry entry = repository.findByAppIdAndKey(appId, tableName + "_schema")
                .orElseThrow(() -> new RecordNotFoundException("Cant find table schema with name " + tableName + "_schema"));

        JsonNode schemaNodes;
        JsonNode nodes;

        // parse schema and validate it
        try {
            schemaNodes = MAPPER.readTree(entry.getValue());
            validateSchema(schemaNodes);
        } catch (InvalidFieldValueException e) {
            throw new InvalidFieldValueException(e.getMessage());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot parse the JSON schema specification for table " + tableName);
        }

        try {
            nodes = MAPPER.readTree(json.toString());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error parsing entity value");
        }

        Map<String, Object> obj = new HashMap<>();

        for (String fieldName : Lists.newArrayList(schemaNodes.fieldNames())) {

            if (!nodes.has(fieldName)) {
                // field was missing, so we look to add it and then to initialize it with a default value
                //  according to its supposed datatype
                obj.put(fieldName, defaultValueForField(fieldName, schemaNodes.get(fieldName).asText()));
            } else {
                // field was there, now just validate it
                validateField(fieldName,
                        schemaNodes.get(fieldName).asText(),
                        nodes.get(fieldName),
                        schemaNodes.get(fieldName).asText().contains(UNIQUE_IDENTIFIER),
                        cxt,
                        updateOperation);
                obj.put(fieldName, nodes.get(fieldName));
            }
        }

        return obj;
    }

    /**
     * Method to add an element (record) to a blob of Json when treating the scratch storage space like a json
     * db.  It validates the incoming 'json' blob against the schema specified in (tableName + _schema) key-value.  If
     * all succeeds, the json block stored in the key name 'tableName' is updated
     *  @param appId     UUID of the scratch storage app
     * @param tableName the table (key name)
     * @param json      the blob of kson to insert
     * @return the json object added or exception
     */
    @Override
    public Object addElement(UUID appId, String tableName, Object json) {

        ScratchStorageEntry entry = repository.findByAppIdAndKey(appId, tableName)
                .orElseThrow(() -> new RecordNotFoundException("Cant find key/table with that name"));

        DocumentContext cxt;
        Object retVal;

        try {
            cxt = JsonPath.using(configuration).parse(entry.getValue());
        } catch (Exception e) {
            throw new InvalidJsonPathQueryException("Can't parse JSON in the table - " + tableName);
        }

        try {
            retVal = validateEntityValue(appId, tableName, json, cxt, false);
            cxt = cxt.add("$", retVal);
        } catch(ResourceAlreadyExistsException e) {
            throw new ResourceAlreadyExistsException(e.getMessage());
        } catch (InvalidFieldValueException e) {
            throw new InvalidFieldValueException(e.getMessage());
        } catch (Exception e) {
            throw new InvalidJsonPathQueryException(e.getMessage());
        }

        try {
            entry.setValue(cxt.jsonString());
            repository.save(entry);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing table contents");
        }

        return retVal;
    }

    /**
     * Removes an element as defined in the jsonPath "path" from specified table name, resultant json is saved
     * back over to the db.
     *
     * @param appId     UUID of the scratch storage app
     * @param tableName table name (key name)
     * @param path      jsonPath to match and remove
     */
    @Override
    public void removeElement(UUID appId, String tableName, String path) {

        ScratchStorageEntry entry = repository.findByAppIdAndKey(appId, tableName)
                .orElseThrow(() -> new RecordNotFoundException("Cant find key/table with that name"));

        DocumentContext cxt;

        try {
            cxt = JsonPath.using(configuration).parse(entry.getValue());
        } catch (Exception e) {
            throw new InvalidJsonPathQueryException("Can't parse JSON in the table - " + tableName);
        }

        if (cxt.read(path) != null) {
            // see if record even exists
            if (cxt.read(path).toString().equals("[]"))
                throw new RecordNotFoundException("Path does not exist");

            // delete the existent record
            cxt = cxt.delete(path);
        } else {
            throw new RecordNotFoundException("Record Not Found");
        }

        try {
            entry.setValue(cxt.jsonString());
            repository.save(entry);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing table contents");
        }
    }

    /**
     * Updates a full record in the given table with the json block "json" after its been validated
     * against the user defined "schema".
     *
     * @param appId     UUID of the application
     * @param tableName table name (key name)
     * @param entityId  id of the row/entity being updated
     * @param json      the block of JSON to update
     * @param path      the json Path to match against to find the point at which to update
     * @returns modified/updated entity or exception
     */
    @Override
    public Object updateElement(UUID appId, String tableName, Object entityId, Object json, String path) {

        ScratchStorageEntry entry = repository.findByAppIdAndKey(appId, tableName)
                .orElseThrow(() -> new RecordNotFoundException("Cant find key/table with that name"));

        DocumentContext cxt;
        Object retVal = null;

        try {
            cxt = JsonPath.using(configuration).parse(entry.getValue());
        } catch (Exception e) {
            throw new InvalidJsonPathQueryException("Can't parse JSON in the table - " + tableName);
        }

        try {
            // make sure the update json has an ID field and its equal to the supplied entity ID
            JsonNode nodes = MAPPER.readTree(json.toString());
            if (!nodes.has(ID_FIELD_NAME) || !nodes.get(ID_FIELD_NAME).textValue().equals(entityId.toString())) {
                throw new InvalidFieldValueException("No ID field provided in update JSON or mismatched from path ID");
            }
        }
        catch (JsonProcessingException e) {
            throw new InvalidFieldValueException("Error validating presence of an ID field in JSON");
        }


        if (cxt.read(path) != null) {
            // check element/entity even exists already
            if (cxt.read(path).toString().equals("[]")) {
                throw new RecordNotFoundException("Path Not Found");
            }

            // once path exists/succeeds, we update in-place
            retVal = validateEntityValue(appId, tableName, json, cxt, true);
            cxt = cxt.set(path, retVal);
        } else {
            throw new RecordNotFoundException("Record Not Found");
        }

        try {
            // re-persist blob
            entry.setValue(cxt.jsonString());
            repository.save(entry);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing table contents");
        }

        return retVal;
    }

    /**
     * Allows to query the json with a jsonpath query
     *
     * @param appId     UUID of the scratch app
     * @param tableName the table name (key name)
     * @param path      the json path query
     * @return the JSON of the matching json path query
     */
    @Override
    public Object queryJson(UUID appId, String tableName, String path) {

        ScratchStorageEntry entry = repository.findByAppIdAndKey(appId, tableName)
                .orElseThrow(() -> new RecordNotFoundException("Cant find key/table with that name"));

        DocumentContext cxt;

        try {
            cxt = JsonPath.using(configuration).parse(entry.getValue());
        } catch (Exception e) {
            throw new InvalidJsonPathQueryException("Can't parse JSON in the table - " + tableName);
        }

        return cxt.read(path);
    }
}

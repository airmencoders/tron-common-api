package mil.tron.commonapi.exception.custom;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mil.tron.commonapi.dto.mapper.DtoMapper;

import org.springframework.validation.FieldError;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@AllArgsConstructor
@NoArgsConstructor
public class TronCommonAppError {
	private static final DtoMapper MODEL_MAPPER = new DtoMapper();

	private static final String ERROR_FIELD = "error";
	private static final String ERRORS_FIELD = "errors";
    private static final String MESSAGE_FIELD = "message";
    private static final String PATH_FIELD = "path";
    private static final String REASON_FIELD = "reason";
    private static final String STATUS_FIELD = "status";
    private static final String TIMESTAMP_FIELD = "timestamp";

    @Getter
    @Setter
    private int status;

    @Getter
    @Setter
    private String reason;

    @Getter
    @Setter
    private String path;

    @Getter
    @Setter
    private String error;
    
    @Getter
    @Setter
    private List<FieldError> errors;
    
    @Getter
    @Setter
    private Date timestamp;

    public static TronCommonAppError fromDefaultAttributeMap(Map<String, Object> defaultErrorAttributes) {

        String reason;
        List<FieldError> objectErrors = null;

        // see if we have binding errors and is able to cast them to an FieldError collection
        try {
            List<FieldError> errors = (List<FieldError>) defaultErrorAttributes.get(ERRORS_FIELD);
            reason = (String) defaultErrorAttributes.get(MESSAGE_FIELD);
            objectErrors = errors;
        }
        catch (Exception e) {
            reason = "";
        }

        // if reason still blank, get Exception type - if its HttpMessageNotReadableException, then make the
        //   error more generalized, instead of a nasty deserialization details message
        if (reason == null || reason.isBlank() || objectErrors == null) {
            String exception = (String) defaultErrorAttributes.getOrDefault("exception", "");
            if (!exception.isBlank() && exception.contains("HttpMessageNotReadableException")) {
                reason = "Could not deserialize request data - check format of the request payload and try again";
            }
            else {
                // fall back is the raw message itself from spring
                reason = (String) defaultErrorAttributes.getOrDefault(MESSAGE_FIELD, "");
            }
        }

        return new TronCommonAppError(((Integer)defaultErrorAttributes.getOrDefault(STATUS_FIELD, "")),
                reason,
                (String) defaultErrorAttributes.getOrDefault(PATH_FIELD, ""),
                (String) defaultErrorAttributes.getOrDefault(ERROR_FIELD, ""),
                objectErrors,
                (Date) defaultErrorAttributes.getOrDefault(TIMESTAMP_FIELD, new Date()));
    }

    public Map<String, Object> toAttributeMap() {
    	Map<String, Object> errorResponse = new LinkedHashMap<>();
    	errorResponse.put(TIMESTAMP_FIELD, this.timestamp);
    	errorResponse.put(STATUS_FIELD, this.status);
    	errorResponse.put(ERROR_FIELD, this.error);
    	errorResponse.put(ERRORS_FIELD, this.errors);
    	errorResponse.put(REASON_FIELD, this.reason);
    	errorResponse.put(PATH_FIELD, this.path);
    	
    	if (this.errors != null && !this.errors.isEmpty()) {
    		List<ValidationError> validationErrors = this.errors.stream()
    				.map(fieldError -> MODEL_MAPPER.map(fieldError, ValidationError.class))
    				.collect(Collectors.toList());
    		
    		errorResponse.put(ERRORS_FIELD, validationErrors);
    	}

    	return errorResponse;
    }

}

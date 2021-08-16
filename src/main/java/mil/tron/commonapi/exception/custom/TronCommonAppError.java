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

	private static final String ERROR = "error";
	public static final String ERRORS = "errors";
	public static final String MESSAGE = "message";
	public static final String PATH = "path";
	public static final String REASON = "reason";
	public static final String STATUS = "status";
	public static final String TIMESTAMP = "timestamp";

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
            List<FieldError> errors = (List<FieldError>) defaultErrorAttributes.get(ERRORS);
            reason = (String) defaultErrorAttributes.get(MESSAGE);
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
                reason = (String) defaultErrorAttributes.getOrDefault(MESSAGE, "");
            }
        }

        return new TronCommonAppError(((Integer)defaultErrorAttributes.getOrDefault(STATUS, "")),
                reason,
                (String) defaultErrorAttributes.getOrDefault(PATH, ""),
                (String) defaultErrorAttributes.getOrDefault(ERROR, ""),
                objectErrors,
                (Date) defaultErrorAttributes.getOrDefault(TIMESTAMP, new Date()));
    }

    public Map<String, Object> toAttributeMap() {
    	Map<String, Object> errorResponse = new LinkedHashMap<>();
    	errorResponse.put(TIMESTAMP, this.timestamp);
    	errorResponse.put(STATUS, this.status);
    	errorResponse.put(ERROR, this.error);
    	errorResponse.put(ERRORS, this.errors);
    	errorResponse.put(REASON, this.reason);
    	errorResponse.put(PATH, this.path);
    	
    	if (this.errors != null && !this.errors.isEmpty()) {
    		List<ValidationError> validationErrors = this.errors.stream()
    				.map(fieldError -> MODEL_MAPPER.map(fieldError, ValidationError.class))
    				.collect(Collectors.toList());
    		
    		errorResponse.put(ERRORS, validationErrors);
    	}

    	return errorResponse;
    }

}

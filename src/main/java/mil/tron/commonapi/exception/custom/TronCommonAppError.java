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
            List<FieldError> errors = (List<FieldError>) defaultErrorAttributes.get("errors");
            reason = (String) defaultErrorAttributes.get("message");
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
                reason = (String) defaultErrorAttributes.getOrDefault("message", "");
            }
        }

        return new TronCommonAppError(((Integer)defaultErrorAttributes.getOrDefault("status", "")),
                reason,
                (String) defaultErrorAttributes.getOrDefault("path", ""),
                (String) defaultErrorAttributes.getOrDefault("error", ""),
                objectErrors,
                (Date) defaultErrorAttributes.getOrDefault("timestamp", new Date()));
    }

    public Map<String, Object> toAttributeMap() {
    	Map<String, Object> errorResponse = new LinkedHashMap<>();
    	errorResponse.put("timestamp", this.timestamp);
    	errorResponse.put("status", this.status);
    	errorResponse.put("error", this.error);
    	errorResponse.put("reason", this.reason);
    	errorResponse.put("path", this.path);
    	
    	if (this.errors != null && !this.errors.isEmpty()) {
    		List<ValidationError> validationErrors = this.errors.stream()
    				.map(fieldError -> MODEL_MAPPER.map(fieldError, ValidationError.class))
    				.collect(Collectors.toList());
    		
    		errorResponse.put("errors", validationErrors);
    	}

    	return errorResponse;
    }

}

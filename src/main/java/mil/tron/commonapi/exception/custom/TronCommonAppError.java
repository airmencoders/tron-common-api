package mil.tron.commonapi.exception.custom;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.validation.FieldError;

import java.util.List;
import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
public class TronCommonAppError {

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

    public static TronCommonAppError fromDefaultAttributeMap(Map<String, Object> defaultErrorAttributes) {

        String reason;

        // see if we have binding errors and is able to cast them to an FieldError collection
        try {
            List<FieldError> errors = (List<FieldError>) defaultErrorAttributes.get("errors");
            reason = errors.get(0).getDefaultMessage();  // this is where validation error details resides
        }
        catch (Exception e) {
            reason = "";
        }

        // if reason still blank, get Exception type - if its HttpMessageNotReadableException, then make the
        //   error more generalized, instead of a nasty deserialization details message
        if (reason == null || reason.isBlank()) {
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
                (String) defaultErrorAttributes.getOrDefault("error", ""));
    }

    public Map<String, Object> toAttributeMap() {
        return Map.of(
                "status", this.status,
                "reason", this.reason,
                "path", this.path,
                "error", this.error
        );
    }

}

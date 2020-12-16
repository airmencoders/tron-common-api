package mil.tron.commonapi.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.BAD_REQUEST)
public class InvalidRecordUpdateRequest extends RuntimeException {
    public InvalidRecordUpdateRequest(String errorMessage) {
        super(errorMessage);
    }
}

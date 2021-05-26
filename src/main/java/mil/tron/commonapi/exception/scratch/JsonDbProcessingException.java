package mil.tron.commonapi.exception.scratch;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class JsonDbProcessingException extends RuntimeException {
    public JsonDbProcessingException(String message) { super(message); }
}

package mil.tron.commonapi.exception.scratch;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidJsonPathQueryException extends RuntimeException {

    public InvalidJsonPathQueryException() { super("Invalid JsonPath Query"); }
    public InvalidJsonPathQueryException(String message) { super(message); }
}

package mil.tron.commonapi.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.BAD_REQUEST)
public class InvalidFieldValueException extends RuntimeException {

    public InvalidFieldValueException() {
        super("Invalid value given for entity field");
    }

    public InvalidFieldValueException(String message) {
        super(message);
    }
}

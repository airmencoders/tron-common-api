package mil.tron.commonapi.exception.webdav;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
public class UnsupportedDavCommandException extends RuntimeException {

    public UnsupportedDavCommandException(String message) {
        super(message);
    }
}

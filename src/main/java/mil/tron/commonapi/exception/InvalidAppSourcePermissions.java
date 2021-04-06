package mil.tron.commonapi.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.FORBIDDEN)
public class InvalidAppSourcePermissions extends RuntimeException {
    public InvalidAppSourcePermissions() {
        super("Invalid User Permissions");
    }

    public InvalidAppSourcePermissions(String msg) {
        super(msg);
    }
}

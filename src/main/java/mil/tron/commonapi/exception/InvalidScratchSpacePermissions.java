package mil.tron.commonapi.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.FORBIDDEN)
public class InvalidScratchSpacePermissions extends RuntimeException {
    public InvalidScratchSpacePermissions() {
        super("Invalid User Permissions");
    }

    public InvalidScratchSpacePermissions(String msg) {
        super(msg);
    }
}

package mil.tron.commonapi.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.UNAUTHORIZED)
public class NotAuthenticatedException extends RuntimeException {
	public NotAuthenticatedException() {
		super("Not Authenticated");
	}
	
	public NotAuthenticatedException(String msg) {
		super(msg);
	}
}

package mil.tron.commonapi.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.FORBIDDEN)
public class NotAuthorizedException extends RuntimeException {
	public NotAuthorizedException() {
		super();
	}
	
	public NotAuthorizedException(String msg) {
		super(msg);
	}
}

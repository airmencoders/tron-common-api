package mil.tron.commonapi.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.CONFLICT)
public class ResourceAlreadyExistsException extends RuntimeException {

	public ResourceAlreadyExistsException() {
		super("Resource already exists");
	}
	
	public ResourceAlreadyExistsException(String msg) {
		super(msg);
	}

}

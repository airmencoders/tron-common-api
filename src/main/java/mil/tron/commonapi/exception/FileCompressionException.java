package mil.tron.commonapi.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.INTERNAL_SERVER_ERROR)
public class FileCompressionException extends RuntimeException {
	public FileCompressionException(String msg) {
		super(msg);
	}
	
	public FileCompressionException(String msg, Throwable cause) {
		super(msg, cause);
	}
}

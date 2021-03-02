package mil.tron.commonapi.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.NOT_FOUND)
public class FileNotExistsException extends RuntimeException {
	public FileNotExistsException(String fileName) {
		super("File not found: " + fileName);
	}
	
	public FileNotExistsException(String fileName, Throwable cause) {
		super("File not found: " + fileName, cause);
	}
}

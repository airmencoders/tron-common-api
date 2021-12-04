package mil.tron.commonapi.exception.documentspace;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.BAD_REQUEST)
public class FolderDepthException extends RuntimeException {
	public FolderDepthException(int maxDepth) {
		super("Creating folder at this location would exceed the max folder depth: " + maxDepth);
	}
	
	public FolderDepthException(String msg) {
		super(msg);
	}
	
	public FolderDepthException(String msg, Throwable cause) {
		super(msg, cause);
	}
}

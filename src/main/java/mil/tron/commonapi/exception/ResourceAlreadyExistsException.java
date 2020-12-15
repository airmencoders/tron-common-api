package mil.tron.commonapi.exception;

public class ResourceAlreadyExistsException extends RuntimeException {

	public ResourceAlreadyExistsException() {
		super("Resource already exists");
	}
	
	public ResourceAlreadyExistsException(String msg) {
		super(msg);
	}

}

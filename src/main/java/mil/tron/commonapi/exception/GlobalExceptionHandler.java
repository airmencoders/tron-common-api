package mil.tron.commonapi.exception;

import javax.validation.ConstraintViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

//	@ExceptionHandler({ ResourceAlreadyExistsException.class })
//	protected ResponseEntity<Object> handleResourceAlreadyExistsException(ResourceAlreadyExistsException ex, WebRequest request) {
//		return new ResponseEntity<>(ex, HttpStatus.CONFLICT);
//	}
	
	@ExceptionHandler({ ConstraintViolationException.class })
	protected ResponseEntity<Object> handleConstraintViolationException(ConstraintViolationException ex, WebRequest request) {
		return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
	}
	
//	@ExceptionHandler({ InvalidRecordUpdateRequest.class })
//	protected ResponseEntity<Object> handleInvalidRecordUpdateRequest(InvalidRecordUpdateRequest ex, WebRequest request) {
//		return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
//	}
	
//	@ExceptionHandler({ RecordNotFoundException.class })
//	protected ResponseEntity<Object> handleRecordNotFoundException(RecordNotFoundException ex, WebRequest request) {
//		return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
//	}
}

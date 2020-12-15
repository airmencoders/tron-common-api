package mil.tron.commonapi.exception;

public class RecordNotFoundException extends RuntimeException {
    public RecordNotFoundException(String errorMessage) {
        super(errorMessage);
    }
}

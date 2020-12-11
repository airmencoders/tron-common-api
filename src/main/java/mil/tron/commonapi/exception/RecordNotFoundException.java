package mil.tron.commonapi.exception;

public class RecordNotFoundException extends Exception {
    public RecordNotFoundException(String errorMessage) {
        super(errorMessage);
    }
}

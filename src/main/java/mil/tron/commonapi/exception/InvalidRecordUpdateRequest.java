package mil.tron.commonapi.exception;

public class InvalidRecordUpdateRequest extends RuntimeException {
    public InvalidRecordUpdateRequest(String errorMessage) {
        super(errorMessage);
    }
}

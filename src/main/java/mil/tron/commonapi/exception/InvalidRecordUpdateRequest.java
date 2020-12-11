package mil.tron.commonapi.exception;

public class InvalidRecordUpdateRequest extends Exception {
    public InvalidRecordUpdateRequest(String errorMessage) {
        super(errorMessage);
    }
}

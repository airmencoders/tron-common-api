package mil.tron.commonapi.controller.advice;

import mil.tron.commonapi.service.trace.ContentTraceManager;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
@Profile("production | development | staging | local")
public class TraceErrorAdvice {

    private ContentTraceManager manager;

    public TraceErrorAdvice(ContentTraceManager manager) {
        this.manager = manager;
    }

    @ExceptionHandler(Exception.class)
    public void logExceptionDetailsFromResponse(Exception ex) throws Exception {  //NOSONAR
        manager.setErrorMessage(ex.getMessage());
        throw ex;
    }
}

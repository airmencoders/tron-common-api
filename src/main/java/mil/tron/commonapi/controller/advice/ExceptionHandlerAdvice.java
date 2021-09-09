package mil.tron.commonapi.controller.advice;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.exception.efa.IllegalModificationBaseException;
import mil.tron.commonapi.exception.efa.IllegalOrganizationModification;
import mil.tron.commonapi.exception.efa.IllegalPersonModification;
import mil.tron.commonapi.service.OrganizationService;
import mil.tron.commonapi.service.PersonService;

@ControllerAdvice
public class ExceptionHandlerAdvice {
	private final OrganizationService orgService;
	private final PersonService personService;
	
	public ExceptionHandlerAdvice(OrganizationService orgService, PersonService personService) {
		this.orgService = orgService;
		this.personService = personService;
	}
	
	/**
	 * Handles EFA related exceptions for the event in which fields were attempted
	 * to be modified without the proper privileges. 
	 * 
	 * @param ex the EFA exception
	 * @param request the request
	 * @return appropriate {@link ResponseEntity} including status code 203, Warning header
	 */
	@ExceptionHandler(value = { IllegalOrganizationModification.class, IllegalPersonModification.class })
	protected ResponseEntity<Object> handleIllegalEntityModification(RuntimeException ex, WebRequest request) {
		IllegalModificationBaseException efaException = (IllegalModificationBaseException)ex;
		
		HttpHeaders headers = new HttpHeaders();
		headers.set("Warning", "214 - Denied Entity Fields: " + String.join(",", efaException.getDeniedFields()));
		
		Object data;
		
		switch (efaException.getEfaType()) {
			case ORGANIZATION:
				data = orgService.convertToDto((Organization)efaException.getData());
				break;
			case PERSON:
				data = personService.convertToDto((Person)efaException.getData(), null);
				break;
			default:
				throw new UnsupportedOperationException(String.format("%s is not a supported EFA Type for exception handling", efaException.getEfaType()));
		}
		
		return ResponseEntity
				.status(HttpStatus.NON_AUTHORITATIVE_INFORMATION)
				.headers(headers)
				.body(data);
	}
}

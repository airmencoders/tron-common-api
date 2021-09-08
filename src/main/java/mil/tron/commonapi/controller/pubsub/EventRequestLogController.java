package mil.tron.commonapi.controller.pubsub;

import java.util.UUID;

import javax.validation.Valid;

import org.springdoc.api.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.annotation.response.WrappedEnvelopeResponse;
import mil.tron.commonapi.annotation.security.PreAuthorizeDashboardAdmin;
import mil.tron.commonapi.annotation.security.PreAuthorizeEventRequestLog;
import mil.tron.commonapi.dto.FilterDto;
import mil.tron.commonapi.dto.pubsub.log.EventRequestLogDto;
import mil.tron.commonapi.dto.pubsub.log.EventRequestLogDtoPaginationResponseWrapper;
import mil.tron.commonapi.exception.ExceptionResponse;
import mil.tron.commonapi.service.pubsub.log.EventRequestService;

@RestController
@RequestMapping({"${api-prefix.v2}/event-request-log"})
public class EventRequestLogController {
	private final EventRequestService eventRequestService;
	
	public EventRequestLogController(EventRequestService eventRequestService) {
		this.eventRequestService = eventRequestService;
	}
	
    @Operation(summary = "Retrieves webhook requests sent to an App Client pubsub event subscriber",
            description = "Any filters creating targeting appClientUser will be ignored")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = EventRequestLogDtoPaginationResponseWrapper.class))),
            @ApiResponse(responseCode = "403",
		            description = "Forbidden -- requester must be the App Client itself or an App Client Developer of the App Client",
		            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @WrappedEnvelopeResponse
    @PreAuthorizeEventRequestLog
    @GetMapping({"/app-client/id/{appId}"})
    public ResponseEntity<Page<EventRequestLogDto>> getEventRequestLogsByAppClientId(
    		@Parameter(description = "App Client ID", required = true) @PathVariable("appId") UUID appClientId,
    		@ParameterObject Pageable page) {
    	return new ResponseEntity<>(eventRequestService.getByAppIdPaged(page, appClientId), HttpStatus.OK);
    }
    
    @Operation(summary = "Retrieves webhook requests sent to an App Client pubsub event subscriber with filtering",
            description = "Any filters creating targeting appClientUser will be ignored")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = EventRequestLogDtoPaginationResponseWrapper.class))),
            @ApiResponse(responseCode = "403",
		            description = "Forbidden -- requester must be the App Client itself or an App Client Developer of the App Client",
		            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @WrappedEnvelopeResponse
    @PreAuthorizeEventRequestLog
    @PostMapping({"/app-client/id/{appId}/filter"})
    public ResponseEntity<Page<EventRequestLogDto>> getEventRequestLogsByAppClientIdWithFilter(
    		@Parameter(description = "App Client ID", required = true) @PathVariable("appId") UUID appClientId,
    		@Parameter(description = "The conditions used to filter", required = true, content = @Content(schema = @Schema(implementation = FilterDto.class)))
				@Valid @RequestBody FilterDto filter,
    		@ParameterObject Pageable page) {
    	return new ResponseEntity<>(eventRequestService.getByAppIdPagedWithSpec(page, appClientId, filter.getFilterCriteria()), HttpStatus.OK);
    }
    
    @Operation(summary = "Retrieves webhook requests sent to the requesting App Client",
            description = "Any filters creating targeting appClientUser will be ignored")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = EventRequestLogDtoPaginationResponseWrapper.class))),
            @ApiResponse(responseCode = "404",
		            description = "Not Found -- no App Client found for the Authenticated user",
		            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @WrappedEnvelopeResponse
    @PreAuthorize("isAuthenticated()")
    @GetMapping({"/app-client/self"})
    public ResponseEntity<Page<EventRequestLogDto>> getEventRequestLogsByAppClient(@ParameterObject Pageable page) {
    	return new ResponseEntity<>(
    			eventRequestService.getByAppNamePaged(page, SecurityContextHolder.getContext().getAuthentication().getName()),
    			HttpStatus.OK);
    }
    
    @Operation(summary = "Retrieves webhook requests sent to the requesting App Client with filtering",
            description = "Any filters creating targeting appClientUser will be ignored")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = EventRequestLogDtoPaginationResponseWrapper.class))),
            @ApiResponse(responseCode = "404",
		            description = "Not Found -- no App Client found for the Authenticated user",
		            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @WrappedEnvelopeResponse
    @PreAuthorize("isAuthenticated()")
    @PostMapping({"/app-client/self/filter"})
    public ResponseEntity<Page<EventRequestLogDto>> getEventRequestLogsByAppClientWithFilter(
    		@Parameter(description = "The conditions used to filter", required = true, content = @Content(schema = @Schema(implementation = FilterDto.class)))
				@Valid @RequestBody FilterDto filter,
    		@ParameterObject Pageable page) {
    	return new ResponseEntity<>(
    			eventRequestService.getByAppNamePagedWithSpec(page, SecurityContextHolder.getContext().getAuthentication().getName(), filter.getFilterCriteria()),
    			HttpStatus.OK);
    }
    
    @Operation(summary = "Retrieves webhook requests sent to all pubsub event subscribers",
            description = "")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = EventRequestLogDtoPaginationResponseWrapper.class))),
            @ApiResponse(responseCode = "403",
		            description = "Forbidden -- requester must have DASHBOARD_ADMIN privilege",
		            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @WrappedEnvelopeResponse
    @PreAuthorizeDashboardAdmin
    @GetMapping({"/all"})
    public ResponseEntity<Page<EventRequestLogDto>> getAllEventRequestLogs(@ParameterObject Pageable page) {
    	return new ResponseEntity<>(eventRequestService.getAllPaged(page), HttpStatus.OK);
    }
    
    @Operation(summary = "Retrieves webhook requests sent to all pubsub event subscribers with filtering",
            description = "")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = EventRequestLogDtoPaginationResponseWrapper.class))),
            @ApiResponse(responseCode = "403",
		            description = "Forbidden -- requester must have DASHBOARD_ADMIN privilege",
		            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @WrappedEnvelopeResponse
    @PreAuthorizeDashboardAdmin
    @PostMapping({"/all/filter"})
    public ResponseEntity<Page<EventRequestLogDto>> getAllEventRequestLogsWithFilter(
    		@Parameter(description = "The conditions used to filter", required = true, content = @Content(schema = @Schema(implementation = FilterDto.class)))
				@Valid @RequestBody FilterDto filter,
    		@ParameterObject Pageable page) {
    	return new ResponseEntity<>(eventRequestService.getAllPagedWithSpec(page, filter.getFilterCriteria()), HttpStatus.OK);
    }
}

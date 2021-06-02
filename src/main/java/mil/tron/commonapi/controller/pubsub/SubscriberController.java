package mil.tron.commonapi.controller.pubsub;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.annotation.pubsub.PreAuthorizeAnyAppClientOrDeveloper;
import mil.tron.commonapi.annotation.response.WrappedEnvelopeResponse;
import mil.tron.commonapi.annotation.security.PreAuthorizeDashboardAdmin;
import mil.tron.commonapi.annotation.pubsub.PreAuthorizeSubscriptionCreation;
import mil.tron.commonapi.annotation.pubsub.PreAuthorizeSubscriptionOwner;
import mil.tron.commonapi.dto.EventInfoDto;
import mil.tron.commonapi.dto.EventInfoDtoResponseWrapper;
import mil.tron.commonapi.dto.pubsub.PubSubLedgerEntryDto;
import mil.tron.commonapi.dto.pubsub.PubSubLedgerEntryDtoResponseWrapper;
import mil.tron.commonapi.dto.pubsub.SubscriberDto;
import mil.tron.commonapi.dto.pubsub.SubscriberDtoResponseWrapper;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.exception.ExceptionResponse;
import mil.tron.commonapi.pubsub.EventManagerService;
import mil.tron.commonapi.service.AppClientUserService;
import mil.tron.commonapi.service.pubsub.SubscriberService;
import mil.tron.commonapi.service.utility.IstioHeaderUtils;
import org.assertj.core.util.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Allows RESTful creation and management of event subscriptions
 */
@RestController
public class SubscriberController {

    @Autowired
    private SubscriberService subService;

    @Autowired
    private AppClientUserService appClientUserService;

    @Autowired
    private EventManagerService eventManagerService;

    /**
     * @deprecated No longer valid T166. See {@link #getAllSubscriptionsWrapped(Authentication authentication)} for new usage.
     * @return
     */
    @Operation(summary = "Retrieves all registered subscriptions", description = "Retrieves all subscriptions")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = SubscriberDto.class))))
    })
    @Deprecated(since = "v2")
    @PreAuthorizeDashboardAdmin
    @GetMapping({"${api-prefix.v1}/subscriptions"})
    public ResponseEntity<Object> getAllSubscriptions() {
        return new ResponseEntity<>(subService.getAllSubscriptions(), HttpStatus.OK);
    }
    
    @Operation(summary = "Retrieves all registered subscriptions", description = "Retrieves all subscriptions")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = SubscriberDtoResponseWrapper.class)))
    })
    @WrappedEnvelopeResponse
    @GetMapping({"${api-prefix.v2}/subscriptions"})
    public ResponseEntity<Object> getAllSubscriptionsWrapped(Authentication authentication) {
        return new ResponseEntity<>(Lists.newArrayList(subService.getAllSubscriptions())
                .stream()
                .filter(item -> authentication  // user is the owning app client itself
                                .getName()
                                .equalsIgnoreCase(IstioHeaderUtils
                                    .extractSubscriberNamespace(item.getSubscriberAddress()))
                            || authentication  // or user is a DASHBOARD_ADMIN
                                .getAuthorities()
                                .stream()
                                .map(GrantedAuthority::getAuthority)
                                .collect(Collectors.toList())
                                    .contains("DASHBOARD_ADMIN")
                            || appClientUserService  // or user is the developer for said application subscription
                                        .userIsAppClientDeveloperForAppSubscription(item.getId(), authentication.getName()))
                .collect(Collectors.toList()), HttpStatus.OK);
    }

    //
    @Operation(summary = "Adds/updates a subscription", description = "Adds a new subscription, or updates an existing subscription")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = SubscriberDto.class)))
    })
    @PreAuthorizeSubscriptionCreation
    @PostMapping({"${api-prefix.v1}/subscriptions", "${api-prefix.v2}/subscriptions"})
    public ResponseEntity<SubscriberDto> createSubscription(@Valid @RequestBody SubscriberDto subscriber) {
        return new ResponseEntity<>(subService.upsertSubscription(subscriber), HttpStatus.OK);
    }

    //
    @Operation(summary = "Retrieves a registered subscription", description = "Retrieve a subscription by its UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = SubscriberDto.class))),
            @ApiResponse(responseCode = "404",
                    description = "Record not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
    })
    @PreAuthorizeSubscriptionOwner
    @GetMapping({"${api-prefix.v1}/subscriptions/{id}", "${api-prefix.v2}/subscriptions/{id}"})
    public ResponseEntity<SubscriberDto> getSubscription(@PathVariable UUID id) {
        return new ResponseEntity<>(subService.getSubscriberById(id), HttpStatus.OK);
    }

    //
    @Operation(summary = "Deletes a subscription", description = "Deletes a subscription by its UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = SubscriberDto.class))),
            @ApiResponse(responseCode = "404",
                    description = "Record not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
    })
    @PreAuthorizeSubscriptionOwner
    @DeleteMapping({"${api-prefix.v1}/subscriptions/{id}", "${api-prefix.v2}/subscriptions/{id}"})
    public ResponseEntity<Object> cancelSubscription(@PathVariable UUID id) {
        subService.cancelSubscription(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    /**
     * @deprecated No longer valid T166. See {@link #getEventSinceDateWrapped(String)} for new usage.
     * @param sinceDate
     * @return
     */
    @Operation(summary = "Retrieves all ledger entries from specified date/time regardless of event type",
            description = "Date/time needs to be in zulu time with format yyyy-MM-ddTHH:mm:ss")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = PubSubLedgerEntryDto.class)))),
            @ApiResponse(responseCode = "400",
                    description = "Bad Request - malformed date/time",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = BadRequestException.class))))
    })
    @Deprecated(since = "v2")
    @GetMapping({"${api-prefix.v1}/subscriptions/events/replay"})
    public ResponseEntity<Object> getEventSinceDate(
            @RequestParam(name="sinceDateTime", required = false) String sinceDate) {

        try {
            // manual string to date conversion to force user's time zone to UTC so no conversion takes place
            //  as opposed to relying on @DateTimeFormat...
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = fmt.parse(sinceDate);
            return new ResponseEntity<>(eventManagerService.getMessagesSinceDateTime(date), HttpStatus.OK);
        } catch (ParseException ex) {
            throw new BadRequestException("Could not convert given date stamp to a valid date/time - check format is yyyy-MM-ddTHH:mm:ss");
        }

    }
    
    @Operation(summary = "Retrieves all ledger entries from specified date/time regardless of event type",
            description = "Date/time needs to be in zulu time with format yyyy-MM-ddTHH:mm:ss")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = PubSubLedgerEntryDtoResponseWrapper.class))),
            @ApiResponse(responseCode = "400",
                    description = "Bad Request - malformed date/time",
                    content = @Content(schema = @Schema(implementation = BadRequestException.class)))
    })
    @WrappedEnvelopeResponse
    @PreAuthorizeAnyAppClientOrDeveloper
    @GetMapping({"${api-prefix.v2}/subscriptions/events/replay"})
    public ResponseEntity<Object> getEventSinceDateWrapped(
            @RequestParam(name="sinceDateTime", required = false) String sinceDate) {

        try {
            // manual string to date conversion to force user's time zone to UTC so no conversion takes place
            //  as opposed to relying on @DateTimeFormat...
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = fmt.parse(sinceDate);
            return new ResponseEntity<>(eventManagerService.getMessagesSinceDateTime(date), HttpStatus.OK);
        } catch (ParseException ex) {
            throw new BadRequestException("Could not convert given date stamp to a valid date/time - check format is yyyy-MM-ddTHH:mm:ss");
        }

    }

    /**
     * @deprecated No longer valid T166. See {@link #getEventsSinceCountAndTypeWrapped(List)} for new usage.
     * @param events
     * @return
     */
    @Operation(summary = "Retrieves all ledger entries from specified event count(s) and event types(s)",
            description = "Simply provide a list of type EventInfoDto containing the event types and the LAST event count received for that event. " +
                            "The returned list will contain, as its start point, the point in time at which the oldest of those event types(s)/event count(s) " +
                            "occurred at - the remainder of that list will be event entries containing only events specified in the request body. Note the event count(s) " +
                            "provided should be equal to the actual count received from Common.  This endpoint will know to return events from that count + 1.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = PubSubLedgerEntryDto.class)))),
            @ApiResponse(responseCode = "400",
                    description = "Bad Request - malformed date/time",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = BadRequestException.class))))
    })
    @Deprecated(since = "v2")
    @PostMapping({"${api-prefix.v1}/subscriptions/events/replay-events"})
    public ResponseEntity<Object> getEventsSinceCountAndType(
            @Parameter(description = "List of events and counts to rewind to and playback", required = true) @Valid @RequestBody List<EventInfoDto> events) {
        return new ResponseEntity<>(eventManagerService.getMessagesSinceEventCountByType(events), HttpStatus.OK);
    }
    
    @Operation(summary = "Retrieves all ledger entries from specified event count(s) and event types(s)",
            description = "Simply provide a list of type EventInfoDto containing the event types and the LAST event count received for that event. " +
                            "The returned list will contain, as its start point, the point in time at which the oldest of those event types(s)/event count(s) " +
                            "occurred at - the remainder of that list will be event entries containing only events specified in the request body. Note the event count(s) " +
                            "provided should be equal to the actual count received from Common.  This endpoint will know to return events from that count + 1.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = PubSubLedgerEntryDtoResponseWrapper.class))),
            @ApiResponse(responseCode = "400",
                    description = "Bad Request - malformed date/time",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = BadRequestException.class))))
    })
    @WrappedEnvelopeResponse
    @PreAuthorizeAnyAppClientOrDeveloper
    @PostMapping({"${api-prefix.v2}/subscriptions/events/replay-events"})
    public ResponseEntity<Object> getEventsSinceCountAndTypeWrapped(
            @Parameter(description = "List of events and counts to rewind to and playback", required = true) @Valid @RequestBody List<EventInfoDto> events) {
        return new ResponseEntity<>(eventManagerService.getMessagesSinceEventCountByType(events), HttpStatus.OK);
    }

    /**
     * @deprecated No longer valid T166. See {@link #getLatestCountsWrapped()} for new usage.
     * @return
     */
    @Operation(summary = "Retrieves most current counts for each event type", description = "Retrieves latest counts for each event type in a key-value pair object")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = EventInfoDto.class))))})
    @Deprecated(since = "v2")
    @GetMapping({"${api-prefix.v1}/subscriptions/events/latest"})
    public ResponseEntity<Object> getLatestCounts() {
        return new ResponseEntity<>(eventManagerService.getEventTypeCounts(), HttpStatus.OK);
    }
    
    @Operation(summary = "Retrieves most current counts for each event type", description = "Retrieves latest counts for each event type in a key-value pair object")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = EventInfoDtoResponseWrapper.class)))})
    @WrappedEnvelopeResponse
    @PreAuthorizeAnyAppClientOrDeveloper
    @GetMapping({"${api-prefix.v2}/subscriptions/events/latest"})
    public ResponseEntity<Object> getLatestCountsWrapped() {
        return new ResponseEntity<>(eventManagerService.getEventTypeCounts(), HttpStatus.OK);
    }
}

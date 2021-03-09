package mil.tron.commonapi.controller.pubsub;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.annotation.security.PreAuthorizeDashboardAdmin;
import mil.tron.commonapi.entity.pubsub.PubSubLedger;
import mil.tron.commonapi.entity.pubsub.Subscriber;
import mil.tron.commonapi.exception.ExceptionResponse;
import mil.tron.commonapi.pubsub.EventManagerService;
import mil.tron.commonapi.service.pubsub.SubscriberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Date;
import java.util.UUID;

/**
 * Allows RESTful creation and management of event subscriptions
 */
@RestController
@RequestMapping("${api-prefix.v1}/subscriptions")
public class SubscriberController {

    @Autowired
    private SubscriberService subService;

    @Autowired
    private EventManagerService eventManagerService;

    @Operation(summary = "Retrieves all registered subscriptions", description = "Retrieves all subscriptions")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = Subscriber.class))))
    })
    @PreAuthorizeDashboardAdmin
    @GetMapping("")
    public ResponseEntity<Object> getAllSubscriptions() {
        return new ResponseEntity<>(subService.getAllSubscriptions(), HttpStatus.OK);
    }

    //
    @Operation(summary = "Adds a new subscription", description = "Adds a new subscription.  Duplicates for the same address/event type won't be accepted")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = Subscriber.class))),
            @ApiResponse(responseCode = "409",
                    description = "Subscription already exists",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PostMapping("")
    public ResponseEntity<Subscriber> createSubscription(@Valid @RequestBody Subscriber subscriber) {
        return new ResponseEntity<>(subService.createSubscription(subscriber), HttpStatus.CREATED);
    }

    //
    @Operation(summary = "Retrieves a registered subscription", description = "Retrieve a subscription by its UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = Subscriber.class))),
            @ApiResponse(responseCode = "404",
                    description = "Record not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
    })
    @GetMapping("/{id}")
    public ResponseEntity<Subscriber> getSubscription(@PathVariable UUID id) {
        return new ResponseEntity<>(subService.getSubscriberById(id), HttpStatus.OK);
    }

    //
    @Operation(summary = "Edits an existing subscription", description = "Edits an existing subscription")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = Subscriber.class))),
            @ApiResponse(responseCode = "400",
                    description = "UUID given does not match UUID in Subscriber entity object",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "Record not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "Subscription exists with address/event type pair",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
    })
    @PutMapping("/{id}")
    public ResponseEntity<Subscriber> updateSubscription(@PathVariable UUID id, @Valid @RequestBody Subscriber subscriber) {
        return new ResponseEntity<>(subService.updateSubscription(id, subscriber), HttpStatus.OK);
    }

    //
    @Operation(summary = "Deletes a subscription", description = "Deletes a subscription by its UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = Subscriber.class))),
            @ApiResponse(responseCode = "404",
                    description = "Record not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> cancelSubscription(@PathVariable UUID id) {
        subService.cancelSubscription(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    //
    @Operation(summary = "Retrieves all legder entries from specified date/time", description = "Retrieves all ledger entries from specified date/time")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = PubSubLedger.class))))
    })
    @GetMapping("/events/replay")
    public ResponseEntity<Object> getEventSinceDate(@RequestParam(name="sinceDateTime") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") Date sinceDate) {
        return new ResponseEntity<>(eventManagerService.getMessagesSinceDateTime(sinceDate), HttpStatus.OK);
    }


    //
    @Operation(summary = "Retrieves most current counts for each event type", description = "Retrieves latest counts for each event type in a key-value pair object")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation")
    })
    @GetMapping("/events/latest")
    public ResponseEntity<Object> getLatestCounts() {
        return new ResponseEntity<>(eventManagerService.getEventTypeCounts(), HttpStatus.OK);
    }
}

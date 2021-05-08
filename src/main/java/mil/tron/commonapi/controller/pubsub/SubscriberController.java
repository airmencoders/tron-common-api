package mil.tron.commonapi.controller.pubsub;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.annotation.security.PreAuthorizeDashboardAdmin;
import mil.tron.commonapi.dto.pubsub.PubSubLedgerEntryDto;
import mil.tron.commonapi.dto.pubsub.SubscriberDto;
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
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = SubscriberDto.class))))
    })
    @PreAuthorizeDashboardAdmin
    @GetMapping("")
    public ResponseEntity<Object> getAllSubscriptions() {
        return new ResponseEntity<>(subService.getAllSubscriptions(), HttpStatus.OK);
    }

    //
    @Operation(summary = "Adds/updates a subscription", description = "Adds a new subscription, or updates an existing subscription")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = SubscriberDto.class)))
    })
    @PostMapping("")
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
    @GetMapping("/{id}")
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
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = PubSubLedgerEntryDto.class))))
    })
    @GetMapping("/events/replay")
    public ResponseEntity<Object> getEventSinceDate(@RequestParam(name="sinceDateTime") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") Date sinceDate) {
        return new ResponseEntity<>(eventManagerService.getMessagesSinceDateTime(sinceDate), HttpStatus.OK);
    }


    //
    @Operation(summary = "Retrieves most current counts for each event type", description = "Retrieves latest counts for each event type in a key-value pair object")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(example="{\n  \"ORGANIZATION_DELETE\": 0,\n  \"PERSON_CHANGE\": 0,\n  \"PERSON_DELETE\": 0,\n  \"PERSON_ORG_ADD\": 0,\n  \"PERSON_ORG_REMOVE\": 0,\n  \"ORGANIZATION_CHANGE\": 0,\n  \"SUB_ORG_REMOVE\": 0,\n  \"SUB_ORG_ADD\": 0\n}")))})
    @GetMapping("/events/latest")
    public ResponseEntity<Object> getLatestCounts() {
        return new ResponseEntity<>(eventManagerService.getEventTypeCounts(), HttpStatus.OK);
    }
}

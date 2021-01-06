package mil.tron.commonapi.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.entity.Airman;
import mil.tron.commonapi.exception.ExceptionResponse;
import mil.tron.commonapi.service.AirmanService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("${api-prefix.v1}/airman")
public class AirmanController {
    private AirmanService airmanService;

    public AirmanController(AirmanService airmanService) {
        this.airmanService = airmanService;
    }

    @Operation(summary = "Retrieves all airmen", description = "Retrieves all airmen records")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = Airman.class))))
    })
    @GetMapping("")
    public ResponseEntity<Iterable<Airman>> getAllAirman() {
        return new ResponseEntity<>(airmanService.getAllAirman(), HttpStatus.OK);
    }

    @Operation(summary = "Retrieves a single airman by UUID", description = "Retrieves single airman record")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = Airman.class))),
            @ApiResponse(responseCode = "404",
                    description = "Resource not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<Airman> getAirman(@Parameter(description = "UUID of the airman record", required= true) @PathVariable UUID id) {
        return new ResponseEntity<>( airmanService.getAirman(id), HttpStatus.OK);
    }

    @Operation(summary = "Adds a new airman", description = "Adds a new airman, ID field should be null for a new addition.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = Airman.class))),
            @ApiResponse(responseCode = "400",
                    description = "Bad Request",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "409",
				description = "Resource already exists with the provided UUID",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
    })
    @PostMapping("")
    public ResponseEntity<Airman> addAirman(@Parameter(description = "Airman record to add", required = true) @Valid @RequestBody Airman airman) {
        return new ResponseEntity<>(airmanService.createAirman(airman), HttpStatus.CREATED);

    }

    @Operation(summary = "Updates an existing airman record", description = "Updates an existing airman")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = Airman.class))),
            @ApiResponse(responseCode = "404",
                    description = "Record not found / Attempt to update airman record with provided UUID does not exist",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Invalid update request - provided UUID didn't exist or did not match UUID in provided record or failed to validate data",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<Airman> updateAirman(@Parameter(description = "Airman record ID to update", required = true) @PathVariable UUID id,
            @Parameter(description = "Airman record data", required = true) @Valid @RequestBody Airman airman) {

        Airman updatedAirman = airmanService.updateAirman(id, airman);
        return new ResponseEntity<>(updatedAirman, HttpStatus.OK);
    }

    @Operation(summary = "Deletes an airman record", description = "Removes an airman record from the database")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation / Request Performed",
                    content = @Content),
            @ApiResponse(responseCode = "404",
                    description = "Record to delete does not exist",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteAirman(@Parameter(description = "UUID id of the airman record to delete", required = true) @PathVariable UUID id) {

        airmanService.removeAirman(id);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Operation(summary = "Adds one or more airmen entities",
            description = "Adds one or more airmen entities - returns that same array of input airmen with their assigned UUIDs. " +
                            "If the request does NOT return 201 (Created) because of an error (see other return codes), then " +
                            "no new airmen will have been committed to the database (if one entity fails, the entire operation fails). " +
                            "The return error message will list the offending UUID or other data that caused the error.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = Airman.class))),
            @ApiResponse(responseCode = "400",
                    description = "Bad data or validation error",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "Bad Request / One of the supplied airman contained a UUID that already exists",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PostMapping("/airmen")
    public ResponseEntity<Object> addAirmen(
            @Parameter(description = "Array of Airman to add", required = true) @Valid @RequestBody List<Airman> airmen) {

        return new ResponseEntity<>(airmanService.bulkAddAirmen(airmen), HttpStatus.CREATED);
    }

}

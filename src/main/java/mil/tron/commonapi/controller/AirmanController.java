package mil.tron.commonapi.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.airman.Airman;
import mil.tron.commonapi.person.Person;
import mil.tron.commonapi.service.AirmanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.net.ssl.HttpsURLConnection;
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
                    content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<Airman> getAirman(@Parameter(description = "UUID of the airman record", required= true) @PathVariable UUID id) {
        Airman airman = airmanService.getAirman(id);
        return airman != null ? new ResponseEntity<>(airman, HttpStatus.OK) : new ResponseEntity(HttpStatus.NOT_FOUND);
    }

    @Operation(summary = "Adds a new airman", description = "Adds a new airman, ID field should be null")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = Airman.class))),
            @ApiResponse(responseCode = "400",
                    description = "Bad Request / Airman with this UUID already exists",
                    content = @Content)
    })
    @PostMapping("")
    public ResponseEntity<Airman> addAirman(@Parameter(description = "Airman record to add", required = true) @RequestBody Airman airman) {
        Airman newAirman = airmanService.createAirman(airman);
        if (newAirman != null) {
            return new ResponseEntity<>(newAirman, HttpStatus.CREATED);
        }
        else {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @Operation(summary = "Updates an existing airman record", description = "Updates an existing airman")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = Airman.class))),
            @ApiResponse(responseCode = "404",
                    description = "Resource not found / Attempt to update airman that does not exist",
                    content = @Content)
    })
    @PutMapping("/{id}")
    public ResponseEntity<Airman> updateAirman(@Parameter(description = "Airman record ID to update", required = true) @PathVariable UUID id,
            @Parameter(description = "Airman record data", required = true) @RequestBody Airman airman) {

        Airman updatedAirman = airmanService.updateAirman(id, airman);
        return updatedAirman != null ? new ResponseEntity<>(updatedAirman, HttpStatus.OK) : new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @Operation(summary = "Deletes an airman record", description = "Removes an airman record from the database")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204",
                    description = "Successful operation / Request Performed",
                    content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteAirman(@Parameter(description = "UUID id of the airman record", required = true) @PathVariable UUID id) {
        airmanService.removeAirman(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

}

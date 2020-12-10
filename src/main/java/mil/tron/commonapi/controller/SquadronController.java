package mil.tron.commonapi.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.squadron.Squadron;
import mil.tron.commonapi.service.SquadronService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("${api-prefix.v1}/squadron")
public class SquadronController {

    private SquadronService squadronService;

    public SquadronController(SquadronService squadronService) {
        this.squadronService = squadronService;
    }

    @Operation(summary = "Retrieves all squadrons", description = "Retrieves all squadron records")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = Squadron.class))))
    })
    @GetMapping("")
    public ResponseEntity<Iterable<Squadron>> getAllSquadrons() {
        return new ResponseEntity<>(squadronService.getAllSquadrons(), HttpStatus.OK);
    }

    @Operation(summary = "Retrieves a single squadron by UUID", description = "Retrieves single squadron record")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = Squadron.class))),
            @ApiResponse(responseCode = "404",
                    description = "Resource not found",
                    content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<Squadron> getSquadron(@Parameter(description = "UUID of the squadron record", required= true) @PathVariable UUID id) {
        Squadron squadron = squadronService.getSquadron(id);
        return squadron != null ? new ResponseEntity<>(squadron, HttpStatus.OK) : new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @Operation(summary = "Adds a new squadron", description = "Adds a new squadron, ID field should be null")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = Squadron.class))),
            @ApiResponse(responseCode = "400",
                    description = "Bad Request / Squadron with this UUID already exists",
                    content = @Content)
    })
    @PostMapping("")
    public ResponseEntity<Squadron> addSquadron(@Parameter(description = "Squadron record to add", required = true) @RequestBody Squadron squadron) {
        Squadron newSquadron = squadronService.createSquadron(squadron);
        if (newSquadron != null) {
            return new ResponseEntity<>(newSquadron, HttpStatus.CREATED);
        }
        else {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @Operation(summary = "Updates an existing squadron record", description = "Updates an existing squadron")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = Squadron.class))),
            @ApiResponse(responseCode = "404",
                    description = "Resource not found / Attempt to update squadron that does not exist",
                    content = @Content)
    })
    @PutMapping("/{id}")
    public ResponseEntity<Squadron> updateSquadron(@Parameter(description = "Squadron record ID to update", required = true) @PathVariable UUID id,
                                               @Parameter(description = "Squadron record data", required = true) @RequestBody Squadron squadron) {

        Squadron updatedSquadron = squadronService.updateSquadron(id, squadron);
        return updatedSquadron != null ? new ResponseEntity<>(updatedSquadron, HttpStatus.OK) : new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @Operation(summary = "Deletes a squadron record", description = "Removes a squadron record from the database")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204",
                    description = "Successful operation / Request Performed",
                    content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteSquadron(@Parameter(description = "UUID id of the squadron record", required = true) @PathVariable UUID id) {
        squadronService.removeSquadron(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}

package mil.tron.commonapi.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.dto.SquadronTerseDto;
import mil.tron.commonapi.entity.Squadron;
import mil.tron.commonapi.service.SquadronService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    public ResponseEntity<Object> getAllSquadrons(
            @Parameter(description = "Retrieves all squadrons, but only lists Entity types by their UUIDs", required = false)
            @RequestParam(name="onlyIds", required = false, defaultValue = "false") Boolean onlyIds) {

        if (onlyIds) {
            List<SquadronTerseDto> slimmedOrgs = new ArrayList<>();
            squadronService.getAllSquadrons().forEach(org -> slimmedOrgs.add(squadronService.convertToDto(org)));
            return new ResponseEntity<>(slimmedOrgs, HttpStatus.OK);
        }
        return new ResponseEntity<>(squadronService.getAllSquadrons(), HttpStatus.OK);
    }

    @Operation(summary = "Retrieves a single squadron by UUID", description = "Retrieves single squadron record")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = Squadron.class))),
            @ApiResponse(responseCode = "404",
                    description = "Resource not found",
                    content = @Content),
            @ApiResponse(responseCode = "400",
                    description = "Bad Request or malformed UUID",
                    content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<Object> getSquadron(@Parameter(description = "UUID of the squadron record", required= true) @PathVariable UUID id,
            @Parameter(description = "Retrieves squadron but only shows UUIDS for person/org types", required = false)
            @RequestParam(name="onlyIds", required = false, defaultValue = "false") Boolean onlyIds) {

        if (onlyIds) {
            return new ResponseEntity<>(squadronService.convertToDto(squadronService.getSquadron(id)), HttpStatus.OK);
        }

        return new ResponseEntity<>(squadronService.getSquadron(id), HttpStatus.OK);
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
        return new ResponseEntity<>(squadronService.createSquadron(squadron), HttpStatus.CREATED);

    }

    @Operation(summary = "Updates an existing squadron record", description = "Updates an existing squadron")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = Squadron.class))),
            @ApiResponse(responseCode = "404",
                    description = "Record not found / Attempt to update squadron that does not exist with provided UUID",
                    content = @Content),
            @ApiResponse(responseCode = "409",
                    description = "Invalid update request - provided UUID didn't exist or did not match UUID in provided record",
                    content = @Content)
    })
    @PutMapping("/{id}")
    public ResponseEntity<Squadron> updateSquadron(@Parameter(description = "Squadron record ID to update", required = true) @PathVariable UUID id,
                                               @Parameter(description = "Squadron record data", required = true) @RequestBody Squadron squadron) {

        return new ResponseEntity<>(squadronService.updateSquadron(id, squadron), HttpStatus.OK);

    }

    @Operation(summary = "Deletes a squadron record", description = "Removes a squadron record from the database")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation / Request Performed",
                    content = @Content),
            @ApiResponse(responseCode = "404",
                    description = "Record to delete does not exist",
                    content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteSquadron(@Parameter(description = "UUID of the squadron record", required = true) @PathVariable UUID id) {

        squadronService.removeSquadron(id);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Operation(summary = "Deletes a member(s) from the squadron", description = "Deletes a member(s) from a squadron")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204",
                    description = "Successful operation",
                    content = @Content),
            @ApiResponse(responseCode = "404",
                    description = "Provided squadron UUID was invalid",
                    content = @Content),
            @ApiResponse(responseCode = "400",
                    description = "Provided airman UUID(s) was/were invalid",
                    content = @Content)
    })
    @DeleteMapping("/{id}/members")
    public ResponseEntity<Object> deleteSquadronMember(@Parameter(description = "UUID of the squadron record", required = true) @PathVariable UUID id,
                                                       @Parameter(description = "UUID(s) of the member(s) to remove", required = true) @RequestBody List<UUID> airmanId) {

        return new ResponseEntity<>(squadronService.removeSquadronMember(id, airmanId), HttpStatus.OK);
    }

    @Operation(summary = "Add member(s) to a squadron", description = "Adds member(s) to a squadron")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204",
                    description = "Successful operation",
                    content = @Content),
            @ApiResponse(responseCode = "404",
                    description = "Provided squadron UUID was invalid",
                    content = @Content),
            @ApiResponse(responseCode = "400",
                    description = "Provided airman UUID(s) was/were invalid",
                    content = @Content)
    })
    @PatchMapping("/{id}/members")
    public ResponseEntity<Object> addSquadronMember(@Parameter(description = "UUID of the squadron record", required = true) @PathVariable UUID id,
                                                       @Parameter(description = "UUID(s) of the member(s) to add", required = true) @RequestBody List<UUID> airmanId) {

        return new ResponseEntity<>(squadronService.addSquadronMember(id, airmanId), HttpStatus.OK);
    }

    @Operation(summary = "Modifies a squadron's attributes", description = "Allows the squadron's attributes to be changed/cleared")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation / Request Performed",
                    content = @Content),
            @ApiResponse(responseCode = "404",
                    description = "Provided UUID did not match any existing squadrons",
                    content = @Content),
            @ApiResponse(responseCode = "400",
                    description = "A provided airman UUID was invalid",
                    content = @Content)
    })
    @PatchMapping("/{squadronId}")
    public ResponseEntity<Object> modifySquadronAttribs(@Parameter(description = "UUID of the squadron to modify", required = true) @PathVariable UUID squadronId,
                                               @Parameter(description = "Object hash containing the keys to modify (set fields to null to clear that field)", required = true) @RequestBody Map<String, String> airmanData) {

        return new ResponseEntity<>(squadronService.modifySquadronAttributes(squadronId, airmanData), HttpStatus.OK);
    }


    @Operation(summary = "Adds one or more squadron entities",
            description = "Adds one or more squadron entities - returns that same array of input squadrons with their assigned UUIDs. " +
                    "If the request does NOT return 201 (Created) because of an error (see other return codes), then " +
                    "any new squadrons up to the organization that caused the failure will have been committed (but none thereafter)" +
                    "The return error message will list the offending UUID or other data that caused the error.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = Squadron.class))),
            @ApiResponse(responseCode = "400",
                    description = "Bad data or validation error",
                    content = @Content),
            @ApiResponse(responseCode = "409",
                    description = "Bad Request / One of the supplied squadrons contained a UUID that already exists or other duplicate data",
                    content = @Content)
    })
    @PostMapping(value = "/squadrons")
    public ResponseEntity<Object> addNewSquadrons(@RequestBody List<Squadron> squads) {
        return new ResponseEntity<>(squadronService.bulkAddSquadrons(squads), HttpStatus.CREATED);
    }

}

package mil.tron.commonapi.controller.usaf;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.dto.OrganizationDto;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.orgtypes.Unit;
import mil.tron.commonapi.exception.ExceptionResponse;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.service.OrganizationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * USAF Group-level facade for Organizations...only deals with orgs of type "Unit.GROUP", otherwise it will error out
 */
@RestController
@RequestMapping("${api-prefix.v1}/group")
public class GroupController {
    private OrganizationService organizationService;
    private static final String UNIT_NOT_A_GROUP = "Organization type given was not a Group";

    public GroupController(OrganizationService organizationService) { this.organizationService = organizationService; }

    @Operation(summary = "Retrieves all organizations of type USAF GROUP",
            description = "Retrieves all USAF WING organizations.  No other parameters allowed, use '/organization' endpoint to allow more options.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = OrganizationDto.class))),
    })
    @GetMapping("")
    public ResponseEntity<Object> getAllGroupTypes() {
        return new ResponseEntity<>(organizationService.getOrganizationsByTypeAndService("", Unit.GROUP, Branch.USAF), HttpStatus.OK);
    }

    @Operation(summary = "Retrieves a USAF GROUP organization by ID", description = "Retrieves a USAF GROUP organization by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = OrganizationDto.class))),
            @ApiResponse(responseCode = "404",
                    description = "Resource not found or UUID exists but is not a Group type",
                    content = @Content),
            @ApiResponse(responseCode = "400",
                    description = "Bad Request or malformed UUID",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<Object> getGroupById(@Parameter(description = "UUID of the organization record", required = true) @PathVariable UUID id) {
        OrganizationDto org = organizationService.getOrganization(id);
        if (org.getOrgType().equals(Unit.GROUP))
            return new ResponseEntity<>(org, HttpStatus.OK);
        else
            throw new RecordNotFoundException("A unit exists by that ID but it is not a Group");
    }

    @Operation(summary = "Adds a new USAF GROUP organization", description = "Adds a new USAF GROUP organization")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = OrganizationDto.class))),
            @ApiResponse(responseCode = "409",
                    description = "Resource already exists with the id provided",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PostMapping("")
    public ResponseEntity<Object> createNewGroup(@Parameter(description = "OrganizationDto object representing new unit", required = true) @RequestBody OrganizationDto org) {
        org.setOrgType(Unit.GROUP);  // force type to group
        org.setBranchType(Branch.USAF); // force branch to USAF
        return new ResponseEntity<>(organizationService.createOrganization(org), HttpStatus.CREATED);
    }

    @Operation(summary = "Updates an existing GROUP", description = "Updates an existing GROUP")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = OrganizationDto.class))),
            @ApiResponse(responseCode = "404",
                    description = "Resource not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Bad request - provided unit not a GROUP",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<Object> updateGroup(@Parameter(description = "UUID of the organization record", required = true) @PathVariable UUID id,
                                              @Parameter(description = "Full OrganizationDto object with updated data in it", required = true) @RequestBody OrganizationDto org) {

        if (org.getOrgType().equals(Unit.GROUP)) {
            OrganizationDto updatedOrg = organizationService.updateOrganization(id, org);

            if (updatedOrg != null)
                return new ResponseEntity<>(updatedOrg, HttpStatus.OK);
            else
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        else
            throw new InvalidRecordUpdateRequest(GroupController.UNIT_NOT_A_GROUP);
    }

    @Operation(summary = "Deletes an existing GROUP", description = "Deletes an existing GROUP")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204",
                    description = "Successful operation",
                    content = @Content),
            @ApiResponse(responseCode = "404",
                    description = "Resource not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Object> deleteGroup(@Parameter(description = "UUID of the host organization record", required = true) @PathVariable UUID id) {

        OrganizationDto org = organizationService.getOrganization(id);

        if (org.getOrgType().equals(Unit.GROUP)) {
            organizationService.deleteOrganization(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
        else {
            throw new InvalidRecordUpdateRequest(GroupController.UNIT_NOT_A_GROUP);
        }
    }

    @Operation(summary = "Deletes a member(s) from the GROUP", description = "Deletes a member(s) from the GROUP")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204",
                    description = "Successful operation",
                    content = @Content),
            @ApiResponse(responseCode = "404",
                    description = "Provided organization UUID was invalid",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Provided person UUID(s) was/were invalid or org was not a GROUP",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @DeleteMapping("/{id}/members")
    public ResponseEntity<Object> deleteGroupMembers(@Parameter(description = "UUID of the host organization record", required = true) @PathVariable UUID id,
                                                     @Parameter(description = "List of UUIDs of persons to remove from the organization", required = true) @RequestBody List<UUID> personId) {

        OrganizationDto org = organizationService.getOrganization(id);
        if (org.getOrgType().equals(Unit.GROUP))
            return new ResponseEntity<>(organizationService.removeOrganizationMember(id, personId), HttpStatus.OK);
        else
            throw new InvalidRecordUpdateRequest(GroupController.UNIT_NOT_A_GROUP);
    }

    @Operation(summary = "Add member(s) to a GROUP", description = "Adds member(s) to a GROUP")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204",
                    description = "Successful operation",
                    content = @Content),
            @ApiResponse(responseCode = "404",
                    description = "A organization UUID was invalid",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Provided person UUID(s) was/were invalid or org was not a GROUP",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PatchMapping("/{id}/members")
    public ResponseEntity<Object> addGroupMembers(@Parameter(description = "UUID of the host organization record", required = true) @PathVariable UUID id,
                                                  @Parameter(description = "List of UUIDs of persons to remove from the organization", required = true) @RequestBody List<UUID> personId) {

        OrganizationDto org = organizationService.getOrganization(id);
        if (org.getOrgType().equals(Unit.GROUP))
            return new ResponseEntity<>(organizationService.addOrganizationMember(id, personId), HttpStatus.OK);
        else
            throw new InvalidRecordUpdateRequest(GroupController.UNIT_NOT_A_GROUP);
    }

    @Operation(summary = "Updates an existing GROUP's attributes", description = "Updates an existing GROUP's attributes")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = OrganizationDto.class))),
            @ApiResponse(responseCode = "404",
                    description = "Organization resource not found",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "A provided person UUID was invalid or org was not a GROUP",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PatchMapping(value = "/{id}")
    public ResponseEntity<OrganizationDto> patchGroup(@Parameter(description = "UUID of the host organization record", required = true) @PathVariable UUID id,
                                                      @Parameter(description = "Object with key value pairs of the attributes to update", required = true) @RequestBody Map<String, String> attribs) {
        OrganizationDto org = organizationService.getOrganization(id);
        if (org.getOrgType().equals(Unit.GROUP))
            return new ResponseEntity<>(organizationService.modify(id, attribs), HttpStatus.OK);
        else
            throw new InvalidRecordUpdateRequest(GroupController.UNIT_NOT_A_GROUP);
    }

    @Operation(summary = "Adds one or more GROUP entities",
            description = "Adds one or more GROUP entities - returns that same array of input organizations with their assigned UUIDs. " +
                    "If the request does NOT return 201 (Created) because of an error (see other return codes), then " +
                    "any new organizations up to that organization that caused the failure will have been committed (but none thereafter)" +
                    "The return error message will list the offending UUID or other data that caused the error.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = OrganizationDto.class))),
            @ApiResponse(responseCode = "400",
                    description = "Bad data or validation error",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "Bad Request / One of the supplied organizations contained a UUID that already exists or other " +
                            "duplicate data / An org was provided that was not a GROUP",
                    content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PostMapping(value = "/groups")
    public ResponseEntity<Object> addNewGroups(@Parameter(description = "List of the new organization objects to add", required = true) @RequestBody List<OrganizationDto> orgs) {
        for (OrganizationDto newOrg : orgs) {
            if (!newOrg.getOrgType().equals(Unit.GROUP))
                throw new InvalidRecordUpdateRequest("One or more provided units were not of type Group");
        }

        return new ResponseEntity<>(organizationService.bulkAddOrgs(orgs), HttpStatus.CREATED);
    }
}

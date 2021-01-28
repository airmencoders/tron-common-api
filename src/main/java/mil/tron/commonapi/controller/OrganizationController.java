package mil.tron.commonapi.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.dto.OrganizationDto;
import mil.tron.commonapi.exception.ExceptionResponse;
import mil.tron.commonapi.service.OrganizationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("${api-prefix.v1}/organization")
public class OrganizationController {
	private OrganizationService organizationService;
	
	public OrganizationController (OrganizationService organizationService) {
		this.organizationService = organizationService;
	}
	
	@Operation(summary = "Retrieves all organizations", description = "Retrieves all organizations")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = OrganizationDto.class)))
	})
	@GetMapping
	public ResponseEntity<Object> getOrganizations() {
			return new ResponseEntity<>(organizationService.getOrganizations(), HttpStatus.OK);

	}
	
	@Operation(summary = "Retrieves an organization by ID", description = "Retrieves an organization by ID")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = OrganizationDto.class))),
			@ApiResponse(responseCode = "404",
					description = "Resource not found",
					content = @Content),
			@ApiResponse(responseCode = "400",
					description = "Bad Request or malformed UUID",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@GetMapping(value = "/{id}")
	public ResponseEntity<OrganizationDto> getOrganization(
			@Parameter(description = "Organization ID to retrieve", required = true) @PathVariable("id") UUID organizationId) {

			return new ResponseEntity<>(organizationService.getOrganization(organizationId), HttpStatus.OK);
	}
	
	@Operation(summary = "Adds an organization", description = "Adds an organization")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "201",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = OrganizationDto.class))),
			@ApiResponse(responseCode = "409",
					description = "Resource already exists with the id provided",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PostMapping
	public ResponseEntity<OrganizationDto> createOrganization(
			@Parameter(description = "Organization to create", required = true) @Valid @RequestBody OrganizationDto organization) {

		return new ResponseEntity<>(organizationService.createOrganization(organization), HttpStatus.CREATED);
	}

	@Operation(summary = "Updates an existing organization", description = "Updates an existing organization")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = OrganizationDto.class))),
			@ApiResponse(responseCode = "404",
					description = "Resource not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "400",
					description = "Bad request",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PutMapping(value = "/{id}")
	public ResponseEntity<OrganizationDto> updateOrganization(
			@Parameter(description = "Organization ID to update", required = true) @PathVariable("id") UUID organizationId,
			@Parameter(description = "Updated organization", required = true) @Valid @RequestBody OrganizationDto organization) {

		OrganizationDto org = organizationService.updateOrganization(organizationId, organization);
		
		if (org != null)
			return new ResponseEntity<>(org, HttpStatus.OK);
		else
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
	}
	
	@Operation(summary = "Deletes an existing organization", description = "Deletes an existing organization")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204",
					description = "Successful operation",
					content = @Content),
			@ApiResponse(responseCode = "404",
					description = "Resource not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@DeleteMapping(value = "/{id}")
	public ResponseEntity<Object> deleteOrganization(
			@Parameter(description = "Organization ID to delete", required = true) @PathVariable("id") UUID organizationId) {
		organizationService.deleteOrganization(organizationId);
		return new ResponseEntity<>(HttpStatus.NO_CONTENT);
	}

	@Operation(summary = "Deletes a member(s) from the organization", description = "Deletes a member(s) from an organization")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204",
					description = "Successful operation",
					content = @Content),
			@ApiResponse(responseCode = "404",
					description = "Provided organization UUID was invalid",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "400",
					description = "Provided person UUID(s) was/were invalid",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@DeleteMapping("/{id}/members")
	public ResponseEntity<Object> deleteOrganizationMember(@Parameter(description = "UUID of the organization to modify", required = true) @PathVariable UUID id,
													   @Parameter(description = "UUID(s) of the member(s) to remove", required = true) @RequestBody List<UUID> personId) {

		return new ResponseEntity<>(organizationService.removeOrganizationMember(id, personId), HttpStatus.OK);
	}

	@Operation(summary = "Add member(s) to an organization", description = "Adds member(s) to an organization")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204",
					description = "Successful operation",
					content = @Content),
			@ApiResponse(responseCode = "404",
					description = "A organization UUID was invalid",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "400",
					description = "Provided person UUID(s) was/were invalid",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PatchMapping("/{id}/members")
	public ResponseEntity<Object> addOrganizationMember(@Parameter(description = "UUID of the organization record", required = true) @PathVariable UUID id,
													@Parameter(description = "UUID(s) of the member(s) to add", required = true) @RequestBody List<UUID> personId) {

		return new ResponseEntity<>(organizationService.addOrganizationMember(id, personId), HttpStatus.OK);
	}

	// TODO : add swagger doc
	@PatchMapping("/{id}/subordinates")
	public ResponseEntity<Object> addSubordinateOrganization(@PathVariable UUID id, @RequestBody List<UUID> orgIds) {
		return new ResponseEntity<>(organizationService.addSubordinateOrg(id, orgIds), HttpStatus.OK);
	}

	// TODO : add swagger doc
	@DeleteMapping("/{id}/subordinates")
	public ResponseEntity<Object> removeSubordinateOrganization(@PathVariable UUID id, @RequestBody List<UUID> orgIds) {
		return new ResponseEntity<>(organizationService.removeSubordinateOrg(id, orgIds), HttpStatus.OK);
	}

	@Operation(summary = "Updates an existing organization's attributes", description = "Updates an existing organization's attributes")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = OrganizationDto.class))),
			@ApiResponse(responseCode = "404",
					description = "Organization resource not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "400",
					description = "A provided person UUID was invalid",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PatchMapping(value = "/{id}")
	public ResponseEntity<OrganizationDto> patchOrganization(
			@Parameter(description = "Organization ID to update", required = true) @PathVariable("id") UUID organizationId,
			@Parameter(description = "Object hash containing the keys to modify (set fields to null to clear that field)", required = true) @RequestBody Map<String, String> attribs) {

			return new ResponseEntity<>(organizationService.modifyAttributes(organizationId, attribs), HttpStatus.OK);
	}

	@Operation(summary = "Adds one or more organization entities",
			description = "Adds one or more organization entities - returns that same array of input organizations with their assigned UUIDs. " +
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
					description = "Bad Request / One of the supplied organizations contained a UUID that already exists or other duplicate data",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PostMapping(value = "/organizations")
	public ResponseEntity<Object> addNewOrganizations(@RequestBody List<OrganizationDto> orgs) {
		return new ResponseEntity<>(organizationService.bulkAddOrgs(orgs), HttpStatus.CREATED);
	}
}

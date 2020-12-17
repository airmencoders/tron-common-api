package mil.tron.commonapi.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.service.OrganizationService;

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
					content = @Content(schema = @Schema(implementation = Organization.class)))
	})
	@GetMapping
	public ResponseEntity<Iterable<Organization>> getOrganizations() {
		return new ResponseEntity<>(organizationService.getOrganizations(), HttpStatus.OK);
	}
	
	@Operation(summary = "Retrieves an organization by ID", description = "Retrieves an organization by ID")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = Organization.class))),
			@ApiResponse(responseCode = "404",
					description = "Resource not found",
					content = @Content)
	})
	@GetMapping(value = "/{id}")
	public ResponseEntity<Organization> getOrganization(
			@Parameter(description = "Organization ID to retrieve", required = true) @PathVariable("id") UUID organizationId) {
		Organization org = organizationService.getOrganization(organizationId);
		
		if (org != null)
			return new ResponseEntity<>(org, HttpStatus.OK);
		else
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
	}
	
	@Operation(summary = "Adds an organization", description = "Adds an organization")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "201",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = Organization.class))),
			@ApiResponse(responseCode = "400",
					description = "Bad request",
					content = @Content)
	})
	@PostMapping
	public ResponseEntity<Organization> createOrganization(
			@Parameter(description = "Organization to create", required = true) @RequestBody Organization organization) {
		Organization createdOrg = organizationService.createOrganization(organization);
		
		if (createdOrg != null)
			return new ResponseEntity<>(createdOrg, HttpStatus.CREATED);
		else
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
	}
	
	@Operation(summary = "Updates an existing organization", description = "Updates an existing organization")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = Organization.class))),
			@ApiResponse(responseCode = "404",
					description = "Resource not found",
					content = @Content)
	})
	@PutMapping(value = "/{id}")
	public ResponseEntity<Organization> updateOrganization(
			@Parameter(description = "Organization ID to update", required = true) @PathVariable("id") UUID organizationId,
			@Parameter(description = "Updated organization", required = true) @RequestBody Organization organization) {
		
		Organization org = organizationService.updateOrganization(organizationId, organization);
		
		if (org != null)
			return new ResponseEntity<>(org, HttpStatus.OK);
		else
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
	}
	
	@Operation(summary = "Deletes an existing organization", description = "Deletes an existing organization")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204",
					description = "Successful operation",
					content = @Content)
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
					content = @Content),
			@ApiResponse(responseCode = "409",
					description = "Provided person UUID(s) was/were invalid",
					content = @Content)
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
					content = @Content),
			@ApiResponse(responseCode = "409",
					description = "Provided person UUID(s) was/were invalid",
					content = @Content)
	})
	@PatchMapping("/{id}/members")
	public ResponseEntity<Object> addOrganizationMember(@Parameter(description = "UUID of the organization record", required = true) @PathVariable UUID id,
													@Parameter(description = "UUID(s) of the member(s) to add", required = true) @RequestBody List<UUID> personId) {

		return new ResponseEntity<>(organizationService.addOrganizationMember(id, personId), HttpStatus.OK);
	}

	@Operation(summary = "Updates an existing organization's attributes", description = "Updates an existing organization's attributes")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = Organization.class))),
			@ApiResponse(responseCode = "404",
					description = "Organization resource not found",
					content = @Content),
			@ApiResponse(responseCode = "409",
					description = "A provided person UUID was invalid",
					content = @Content)
	})
	@PatchMapping(value = "/{id}")
	public ResponseEntity<Organization> patchOrganization(
			@Parameter(description = "Organization ID to update", required = true) @PathVariable("id") UUID organizationId,
			@Parameter(description = "Object hash containing the keys to modify (set fields to null to clear that field)", required = true) @RequestBody Map<String, String> attribs) {

			return new ResponseEntity<>(organizationService.modifyAttributes(organizationId, attribs), HttpStatus.OK);
	}
}

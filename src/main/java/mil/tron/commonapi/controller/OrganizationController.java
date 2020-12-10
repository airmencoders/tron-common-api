package mil.tron.commonapi.controller;

import java.util.Collection;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.organization.Organization;
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
	public ResponseEntity<Collection<Organization>> getOrganizations() {
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
		return new ResponseEntity<>(HttpStatus.NO_CONTENT);
	}
	
}

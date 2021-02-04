package mil.tron.commonapi.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.dto.OrganizationDto;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.orgtypes.Unit;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.exception.ExceptionResponse;
import mil.tron.commonapi.service.OrganizationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.*;

@RestController
@RequestMapping("${api-prefix.v1}/organization")
public class OrganizationController {
	private OrganizationService organizationService;
	private static final String UNKNOWN_TYPE = "UNKNOWN";
	
	public OrganizationController (OrganizationService organizationService) {
		this.organizationService = organizationService;
	}
	
	@Operation(summary = "Retrieves all organizations",
			description = "Retrieves all organizations.  Optionally can provide 'type' parameter (e.g. 'WING') to filter by Organization type " +
						"and/or 'branch' parameter to filter by branch of service (e.g 'USAF'). If neither parameter is given, then no filters " +
						"are applied and request returns all Organizations.  Optionally can also provide 'search' parameter to search on organization " +
						"names within the result set (case in-sensitive).")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = OrganizationDto.class))),
			@ApiResponse(responseCode = "400",
					description = "Bad Request - likely due to invalid unit type or branch of service specified",
					content = @Content(schema = @Schema(implementation = OrganizationDto.class)))
	})
	@GetMapping
	public ResponseEntity<Object> getOrganizations(
			@Parameter(description = "Unit type to filter on", required = false, content= @Content(schema =  @Schema(implementation = Unit.class)))
				@RequestParam(name = "type", required = false, defaultValue = OrganizationController.UNKNOWN_TYPE) String unitType,
			@Parameter(description = "Branch type to filter on", required = false, content= @Content(schema =  @Schema(implementation = Branch.class)))
				@RequestParam(name = "branch", required = false, defaultValue = OrganizationController.UNKNOWN_TYPE) String branchType,
			@Parameter(description = "Case insensitive search string for org name", required = false)
				@RequestParam(name = "search", required = false, defaultValue = "") String searchQuery,
			@Parameter(description = "Comma-separated string list to include in Person type sub-fields. Example: people=id,firstName,lastName", required = false)
				@RequestParam(name="people", required = false, defaultValue = "") String peopleFields,
			@Parameter(description = "Comma-separated string list to include in Organization type sub-fields. Example: organizations=id,name", required = false)
				@RequestParam(name="organizations", required = false, defaultValue = "") String orgFields) {

		// return all types by default (if no query params given)
		if (unitType.equals(OrganizationController.UNKNOWN_TYPE) && branchType.equals(OrganizationController.UNKNOWN_TYPE)) {
			Iterable<OrganizationDto> allOrgs = organizationService.getOrganizations(searchQuery);

			if (!peopleFields.isEmpty() || !orgFields.isEmpty()) {
				// for each item, customize the entity
				List<JsonNode> customizedList = new ArrayList<>();
				allOrgs.forEach(item -> customizedList.add(
						organizationService.customizeEntity(
								initCustomizationOptions(peopleFields, orgFields), item)));
				return new ResponseEntity<>(customizedList, HttpStatus.OK);
			}

			// otherwise return list of DTOs
			return new ResponseEntity<>(allOrgs, HttpStatus.OK);
		}
		// otherwise try to return the types specified
		else {
			Unit unit;
			Branch branch;

			// coerce types to enumerated value
			try {
				unit = unitType.equals(OrganizationController.UNKNOWN_TYPE) ? null : Unit.valueOf(unitType.toUpperCase());
				branch = branchType.equals(OrganizationController.UNKNOWN_TYPE) ? null : Branch.valueOf(branchType.toUpperCase());
			}
			catch (IllegalArgumentException e) {
				throw new BadRequestException("Invalid branch or service type given");
			}

			Iterable<OrganizationDto> allFilteredOrgs = organizationService.getOrganizationsByTypeAndService(searchQuery, unit, branch);

			if (!peopleFields.isEmpty() || !orgFields.isEmpty()) {
				// for each dto, customize it
				List<JsonNode> customizedList = new ArrayList<>();
				allFilteredOrgs.forEach(
						item -> customizedList.add(
								organizationService.customizeEntity(
										initCustomizationOptions(peopleFields, orgFields), item)));
				return new ResponseEntity<>(customizedList, HttpStatus.OK);
			}

			// otherwise return the list of filtered DTOs
			return new ResponseEntity<>(allFilteredOrgs, HttpStatus.OK);
		}
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
	public ResponseEntity<Object> getOrganization(
			@Parameter(description = "Organization ID to retrieve", required = true) @PathVariable("id") UUID organizationId,
			@Parameter(description = "Whether to flatten out all attached members and organizations contained therein", required = false)
				@RequestParam(name = "flatten", required = false, defaultValue = "false") boolean flatten,
			@Parameter(description = "Comma-separated string list of fields to include in Person type sub-fields. Example: people=id,firstName,lastName", required = false)
					@RequestParam(name="people", required = false, defaultValue = "") String peopleFields,
			@Parameter(description = "Comma-separated string list of fields to include in Organizational type sub-fields. Example: organizations=id,name", required = false)
				@RequestParam(name="organizations", required = false, defaultValue = "") String orgFields) {

		OrganizationDto org = organizationService.getOrganization(organizationId);

		if (flatten) {

			if (!peopleFields.isEmpty() || !orgFields.isEmpty()) {
				// flatten first, then customize that entity
				return new ResponseEntity<>(
						organizationService.customizeEntity(
								initCustomizationOptions(peopleFields, orgFields), flattenOrg(org)), HttpStatus.OK);
			}

			// otherwise return flattened org as a regular DTO
			return new ResponseEntity<>(flattenOrg(org), HttpStatus.OK);
		}
		else {
			if (!peopleFields.isEmpty() || !orgFields.isEmpty()) {
				return new ResponseEntity<>(
						organizationService.customizeEntity(
								initCustomizationOptions(peopleFields, orgFields), org), HttpStatus.OK);
			}

			// otherwise return org DTO as-is
			return new ResponseEntity<>(org, HttpStatus.OK);
		}
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

	@Operation(summary = "Add subordinate organizations to an organization", description = "Adds subordinate orgs to an organization")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204",
					description = "Successful operation",
					content = @Content),
			@ApiResponse(responseCode = "404",
					description = "Host organization UUID was invalid",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "400",
					description = "Provided org UUID(s) was/were invalid",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PatchMapping("/{id}/subordinates")
	public ResponseEntity<Object> addSubordinateOrganization(@Parameter(description = "UUID of the host organization record", required = true) @PathVariable UUID id,
															 @Parameter(description = "UUID(s) of subordinate organizations", required = true) @RequestBody List<UUID> orgIds) {

		return new ResponseEntity<>(organizationService.addSubordinateOrg(id, orgIds), HttpStatus.OK);
	}

	@Operation(summary = "Remove subordinate organizations from an organization", description = "Removes subordinate orgs from an organization")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204",
					description = "Successful operation",
					content = @Content),
			@ApiResponse(responseCode = "404",
					description = "Host organization UUID was invalid",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "400",
					description = "Provided org UUID(s) was/were invalid",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@DeleteMapping("/{id}/subordinates")
	public ResponseEntity<Object> removeSubordinateOrganization(@Parameter(description = "UUID of the host organization record", required = true) @PathVariable UUID id,
																@Parameter(description = "UUID(s) of subordinate organizations", required = true) @RequestBody List<UUID> orgIds) {

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

	private OrganizationDto flattenOrg(OrganizationDto org) {
		OrganizationDto flattenedOrg = new OrganizationDto();

		// copy over the basic info first
		flattenedOrg.setBranchType(org.getBranchType());
		flattenedOrg.setOrgType(org.getOrgType());
		flattenedOrg.setParentOrganizationUUID(org.getParentOrganization());
		flattenedOrg.setId(org.getId());
		flattenedOrg.setLeaderUUID(org.getLeader());
		flattenedOrg.setName(org.getName());
		flattenedOrg.setSubOrgsUUID(harvestOrgSubordinateUnits(org.getSubordinateOrganizations(), new HashSet<>()));
		flattenedOrg.setMembersUUID(harvestOrgMembers(org.getSubordinateOrganizations(), new HashSet<>()));
		return flattenedOrg;
	}

	// recursive helper function to dig deep on a units subordinates
	private Set<UUID> harvestOrgSubordinateUnits(Set<UUID> orgIds, Set<UUID> accumulator) {

		if (orgIds == null || orgIds.isEmpty()) return accumulator;

		for (UUID orgId : orgIds) {
			accumulator.add(orgId);
			Set<UUID> ids = harvestOrgSubordinateUnits(organizationService.getOrganization(orgId).getSubordinateOrganizations(), new HashSet<>());
			accumulator.addAll(ids);
		}

		return accumulator;
	}

	// recursive helper function to dig deep on a units subordinates
	private Set<UUID> harvestOrgMembers(Set<UUID> orgIds, Set<UUID> accumulator) {

		if (orgIds == null || orgIds.isEmpty()) return accumulator;

		for (UUID orgId : orgIds) {
			OrganizationDto subOrg = organizationService.getOrganization(orgId);
			if (subOrg.getLeader() != null) accumulator.add(subOrg.getLeader());  // make sure to roll up the leader if there is one
			if (subOrg.getMembers() != null) accumulator.addAll(subOrg.getMembers());
			Set<UUID> ids = harvestOrgMembers(organizationService.getOrganization(orgId).getSubordinateOrganizations(), new HashSet<>());
			accumulator.addAll(ids);
		}

		return accumulator;
	}

	// helper to build the options map for customization of return DTOs
	private Map<String, String> initCustomizationOptions(String peopleFields, String orgFields) {
		// we have some customizations specified from the user...
		Map<String, String> fields = new HashMap<>();
		fields.put("people", peopleFields);
		fields.put("orgs", orgFields);
		return fields;
	}
}

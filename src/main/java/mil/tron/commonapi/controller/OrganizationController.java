package mil.tron.commonapi.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonpatch.JsonPatch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.annotation.response.WrappedEnvelopeResponse;
import mil.tron.commonapi.annotation.security.PreAuthorizeOrganizationCreate;
import mil.tron.commonapi.annotation.security.PreAuthorizeOrganizationDelete;
import mil.tron.commonapi.annotation.security.PreAuthorizeOrganizationEdit;
import mil.tron.commonapi.annotation.security.PreAuthorizeOrganizationRead;
import mil.tron.commonapi.dto.FilterDto;
import mil.tron.commonapi.dto.OrganizationDto;
import mil.tron.commonapi.dto.OrganizationDtoPaginationResponseWrapper;
import mil.tron.commonapi.dto.OrganizationDtoResponseWrapper;
import mil.tron.commonapi.dto.annotation.helper.JsonPatchObjectArrayValue;
import mil.tron.commonapi.dto.annotation.helper.JsonPatchObjectValue;
import mil.tron.commonapi.dto.annotation.helper.JsonPatchStringArrayValue;
import mil.tron.commonapi.dto.annotation.helper.JsonPatchStringValue;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.orgtypes.Unit;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.exception.ExceptionResponse;
import mil.tron.commonapi.service.OrganizationService;
import org.springdoc.api.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.*;

@RestController
public class OrganizationController {
	private OrganizationService organizationService;

	public static final String PEOPLE_PARAMS_FIELD = "people";
	public static final String ORGS_PARAMS_FIELD = "organizations";
	private static final String UNKNOWN_TYPE = "UNKNOWN";
	
	public OrganizationController (OrganizationService organizationService) {
		this.organizationService = organizationService;
	}
	
	/**
	 * @deprecated No longer acceptable as it returns array json data as top most parent.
	 * @param unitType
	 * @param branchType
	 * @param searchQuery
	 * @param peopleFields
	 * @param orgFields
	 * @param page
	 * @return
	 */
	@Operation(summary = "Retrieves all organizations",
			description = "Retrieves all organizations.  Optionally can provide 'type' parameter (e.g. 'WING') to filter by Organization type " +
						"and/or 'branch' parameter to filter by branch of service (e.g 'USAF'). If neither parameter is given, then no filters " +
						"are applied and request returns all Organizations.  Optionally can also provide 'search' parameter to search on organization " +
						"names within the result set (case in-sensitive).")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(array = @ArraySchema(schema = @Schema(implementation = OrganizationDto.class)))),
			@ApiResponse(responseCode = "400",
					description = "Bad Request - likely due to invalid unit type or branch of service specified",
					content = @Content(schema = @Schema(implementation = OrganizationDto.class)))
	})
	@PreAuthorizeOrganizationRead
	@Deprecated(since = "${api-prefix.v2}")
	@GetMapping("${api-prefix.v1}/organization")
	public ResponseEntity<Object> getOrganizations(
			@Parameter(description = "Unit type to filter on", required = false, content= @Content(schema =  @Schema(implementation = Unit.class)))
				@RequestParam(name = "type", required = false, defaultValue = OrganizationController.UNKNOWN_TYPE) String unitType,
			@Parameter(description = "Branch type to filter on", required = false, content= @Content(schema =  @Schema(implementation = Branch.class)))
				@RequestParam(name = "branch", required = false, defaultValue = OrganizationController.UNKNOWN_TYPE) String branchType,
			@Parameter(description = "Case insensitive search string for org name", required = false)
				@RequestParam(name = "search", required = false, defaultValue = "") String searchQuery,
			@Parameter(description = "Comma-separated string list to include in Person type sub-fields. Example: people=id,firstName,lastName", required = false)
				@RequestParam(name = OrganizationController.PEOPLE_PARAMS_FIELD, required = false, defaultValue = "") String peopleFields,
			@Parameter(description = "Comma-separated string list to include in Organization type sub-fields. Example: organizations=id,name", required = false)
				@RequestParam(name = OrganizationController.ORGS_PARAMS_FIELD, required = false, defaultValue = "") String orgFields,
				@ParameterObject Pageable page) {

			// return all types by default (if no query params given)
		if (unitType.equals(OrganizationController.UNKNOWN_TYPE) && branchType.equals(OrganizationController.UNKNOWN_TYPE)) {
			Iterable<OrganizationDto> allOrgs = organizationService.getOrganizations(searchQuery, page);

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

			Iterable<OrganizationDto> allFilteredOrgs = organizationService.getOrganizationsByTypeAndService(searchQuery, unit, branch, page);

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
	
	@Operation(summary = "Retrieves all organizations",
			description = "Retrieves all organizations.  Optionally can provide 'type' parameter (e.g. 'WING') to filter by Organization type " +
						"and/or 'branch' parameter to filter by branch of service (e.g 'USAF'). If neither parameter is given, then no filters " +
						"are applied and request returns all Organizations.  Optionally can also provide 'search' parameter to search on organization " +
						"names within the result set (case in-sensitive).")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = OrganizationDtoPaginationResponseWrapper.class)),
					headers = @Header(
							name="link",
							description = "Contains the appropriate pagination links if application. "
									+ "If no pagination query params given, then no pagination links will exist. "
									+ "Possible rel values include: first, last, prev, next",
							schema = @Schema(type = "string"))),
			@ApiResponse(responseCode = "400",
					description = "Bad Request - likely due to invalid unit type or branch of service specified",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorizeOrganizationRead
	@WrappedEnvelopeResponse
	@GetMapping("${api-prefix.v2}/organization")
	public ResponseEntity<Object> getOrganizationsWrapped(
			@Parameter(description = "Unit type to filter on", required = false, content= @Content(schema =  @Schema(implementation = Unit.class)))
				@RequestParam(name = "type", required = false, defaultValue = OrganizationController.UNKNOWN_TYPE) String unitType,
			@Parameter(description = "Branch type to filter on", required = false, content= @Content(schema =  @Schema(implementation = Branch.class)))
				@RequestParam(name = "branch", required = false, defaultValue = OrganizationController.UNKNOWN_TYPE) String branchType,
			@Parameter(description = "Case insensitive search string for org name", required = false)
				@RequestParam(name = "search", required = false, defaultValue = "") String searchQuery,
			@Parameter(description = "Comma-separated string list to include in Person type sub-fields. Example: people=id,firstName,lastName", required = false)
				@RequestParam(name = OrganizationController.PEOPLE_PARAMS_FIELD, required = false, defaultValue = "") String peopleFields,
			@Parameter(description = "Comma-separated string list to include in Organization type sub-fields. Example: organizations=id,name", required = false)
				@RequestParam(name = OrganizationController.ORGS_PARAMS_FIELD, required = false, defaultValue = "") String orgFields,
				@ParameterObject Pageable page) {

			// return all types by default (if no query params given)
		if (unitType.equals(OrganizationController.UNKNOWN_TYPE) && branchType.equals(OrganizationController.UNKNOWN_TYPE)) {
			Page<OrganizationDto> allOrgs = organizationService.getOrganizationsPage(searchQuery, page);

			if (!peopleFields.isEmpty() || !orgFields.isEmpty()) {
				// for each item, customize the entity
				List<JsonNode> customizedList = new ArrayList<>();
				allOrgs.forEach(item -> customizedList.add(
						organizationService.customizeEntity(
								initCustomizationOptions(peopleFields, orgFields), item)));
				
				Page<JsonNode> customPage = new PageImpl<>(customizedList, page, allOrgs.getTotalElements());
				return new ResponseEntity<>(customPage, HttpStatus.OK);
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

			Page<OrganizationDto> allFilteredOrgs = organizationService.getOrganizationsByTypeAndServicePage(searchQuery, unit, branch, page);

			if (!peopleFields.isEmpty() || !orgFields.isEmpty()) {
				// for each dto, customize it
				List<JsonNode> customizedList = new ArrayList<>();
				allFilteredOrgs.forEach(
						item -> customizedList.add(
								organizationService.customizeEntity(
										initCustomizationOptions(peopleFields, orgFields), item)));
				
				Page<JsonNode> customPage = new PageImpl<>(customizedList, page, allFilteredOrgs.getTotalElements());
				return new ResponseEntity<>(customPage, HttpStatus.OK);
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
	@PreAuthorizeOrganizationRead
	@GetMapping({"${api-prefix.v1}/organization/{id}", "${api-prefix.v2}/organization/{id}"})
	public ResponseEntity<Object> getOrganization(
			@Parameter(description = "Organization ID to retrieve", required = true) @PathVariable("id") UUID organizationId,
			@Parameter(description = "Whether to flatten out all attached members and organizations contained therein", required = false)
				@RequestParam(name = "flatten", required = false, defaultValue = "false") boolean flatten,
			@Parameter(description = "Comma-separated string list of fields to include in Person type sub-fields. Example: people=id,firstName,lastName", required = false)
					@RequestParam(name=OrganizationController.PEOPLE_PARAMS_FIELD, required = false, defaultValue = "") String peopleFields,
			@Parameter(description = "Comma-separated string list of fields to include in Organizational type sub-fields. Example: organizations=id,name", required = false)
				@RequestParam(name=OrganizationController.ORGS_PARAMS_FIELD, required = false, defaultValue = "") String orgFields) {

		OrganizationDto org = organizationService.getOrganization(organizationId);

		if (flatten) {

			if (!peopleFields.isEmpty() || !orgFields.isEmpty()) {
				// flatten first, then customize that entity
				return new ResponseEntity<>(
						organizationService.customizeEntity(
								initCustomizationOptions(peopleFields, orgFields), organizationService.flattenOrg(org)), HttpStatus.OK);
			}

			// otherwise return flattened org as a regular DTO
			return new ResponseEntity<>(organizationService.flattenOrg(org), HttpStatus.OK);
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
			@ApiResponse(responseCode = "203",
					description = "Successful - Entity Field Authority denied access to some fields",
					content = @Content(schema = @Schema(implementation = OrganizationDto.class))),
			@ApiResponse(responseCode = "201",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = OrganizationDto.class))),
			@ApiResponse(responseCode = "409",
					description = "Resource already exists with the id provided",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorizeOrganizationCreate
	@PostMapping({"${api-prefix.v1}/organization", "${api-prefix.v2}/organization"})
	public ResponseEntity<OrganizationDto> createOrganization(
			@Parameter(description = "Organization to create",
				required = true,
				schema = @Schema(implementation = OrganizationDto.class)) @Valid @RequestBody OrganizationDto organization) {

		return new ResponseEntity<>(organizationService.createOrganization(organization), HttpStatus.CREATED);
	}

	@Operation(summary = "Updates an existing organization", description = "Updates an existing organization")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "203",
					description = "Successful - Entity Field Authority denied access to some fields",
					content = @Content(schema = @Schema(implementation = OrganizationDto.class))),
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
	@PreAuthorizeOrganizationEdit
	@PutMapping({"${api-prefix.v1}/organization/{id}", "${api-prefix.v2}/organization/{id}"})
	public ResponseEntity<OrganizationDto> updateOrganization(
			@Parameter(description = "Organization ID to update", required = true) @PathVariable("id") UUID organizationId,
			@Parameter(description = "Updated organization",
				required = true,
				schema = @Schema(implementation = OrganizationDto.class))
				@Valid @RequestBody OrganizationDto organization) {

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
	@PreAuthorizeOrganizationDelete
	@DeleteMapping({"${api-prefix.v1}/organization/{id}", "${api-prefix.v2}/organization/{id}"})
	public ResponseEntity<Object> deleteOrganization(
			@Parameter(description = "Organization ID to delete", required = true) @PathVariable("id") UUID organizationId) {
		organizationService.deleteOrganization(organizationId);
		return new ResponseEntity<>(HttpStatus.NO_CONTENT);
	}

	@Operation(summary = "Deletes a leader from an organization", description = "Deletes/clears out the leader position with no one")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = OrganizationDto.class))),
			@ApiResponse(responseCode = "404",
					description = "Organization not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
					description = "Not Authorized - do not have privilege to edit this field",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
	})
	@PreAuthorizeOrganizationEdit
	@DeleteMapping({"${api-prefix.v1}/organization/{id}/leader", "${api-prefix.v2}/organization/{id}/leader"})
	public ResponseEntity<Object> deleteOrgLeader(
			@Parameter(description = "Organization ID to delete the leader from", required = true) @PathVariable("id") UUID organizationId) {
		return new ResponseEntity<>(organizationService.removeLeader(organizationId), HttpStatus.OK);
	}

	@Operation(summary = "Deletes a parent from a subordinate organization", description = "Deletes/clears out the parent org with no org")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = OrganizationDto.class))),
			@ApiResponse(responseCode = "404",
					description = "Organization not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
					description = "Not Authorized - do not have privilege to edit this field",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
	})
	@PreAuthorizeOrganizationEdit
	@DeleteMapping({"${api-prefix.v1}/organization/{id}/parent", "${api-prefix.v2}/organization/{id}/parent"})
	public ResponseEntity<Object> deleteOrgParent(
			@Parameter(description = "Organization ID to delete the parent from", required = true) @PathVariable("id") UUID organizationId) {
		return new ResponseEntity<>(organizationService.removeParentOrganization(organizationId), HttpStatus.OK);
	}

	@Operation(summary = "Deletes a member(s) from the organization", description = "Deletes a member(s) from an organization")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = OrganizationDto.class))),
			@ApiResponse(responseCode = "404",
					description = "Provided organization UUID was invalid",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "400",
					description = "Provided person UUID(s) was/were invalid",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
				description = "Not Authorized - do not have privilege to edit this field",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorizeOrganizationEdit
	@DeleteMapping({"${api-prefix.v1}/organization/{id}/members", "${api-prefix.v2}/organization/{id}/members"})
	public ResponseEntity<Object> deleteOrganizationMember(@Parameter(description = "UUID of the organization to modify", required = true) @PathVariable UUID id,
													   @Parameter(description = "UUID(s) of the member(s) to remove", required = true) @RequestBody List<UUID> personId) {

		return new ResponseEntity<>(organizationService.removeOrganizationMember(id, personId), HttpStatus.OK);
	}

	@Operation(summary = "Add member(s) to an organization", description = "Adds member(s) to an organization")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "403",
					description = "Not Authorized - do not have privilege to edit this field",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = OrganizationDto.class))),
			@ApiResponse(responseCode = "404",
					description = "A organization UUID was invalid",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "400",
					description = "Provided person UUID(s) was/were invalid",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorizeOrganizationEdit
	@PatchMapping({"${api-prefix.v1}/organization/{id}/members", "${api-prefix.v2}/organization/{id}/members"})
	public ResponseEntity<Object> addOrganizationMember(
        @Parameter(description = "UUID of the organization record", required = true) @PathVariable UUID id,
        @Parameter(description = "UUID(s) of the member(s) to add", required = true) @RequestBody List<UUID> personId,
		@Parameter(description = "Whether to make the organization the primary organization for the user", required = false)
				@RequestParam(name = "primary", required = false, defaultValue = "true") boolean primary) {

		return new ResponseEntity<>(organizationService.addOrganizationMember(id, personId, primary), HttpStatus.OK);
	}

	@Operation(summary = "Add subordinate organizations to an organization", description = "Adds subordinate orgs to an organization")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "403",
					description = "Not Authorized - do not have privilege to edit this field",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = OrganizationDto.class))),
			@ApiResponse(responseCode = "404",
					description = "Host organization UUID was invalid",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "400",
					description = "Provided org UUID(s) was/were invalid",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorizeOrganizationEdit
	@PatchMapping({"${api-prefix.v1}/organization/{id}/subordinates", "${api-prefix.v2}/organization/{id}/subordinates"})
	public ResponseEntity<Object> addSubordinateOrganization(
			@Parameter(description = "UUID of the host organization record", required = true) @PathVariable UUID id,
		 	@Parameter(description = "UUID(s) of subordinate organizations", required = true) @RequestBody List<UUID> orgIds) {

		return new ResponseEntity<>(organizationService.addSubordinateOrg(id, orgIds), HttpStatus.OK);
	}

	@Operation(summary = "Remove subordinate organizations from an organization", description = "Removes subordinate orgs from an organization")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = OrganizationDto.class))),
			@ApiResponse(responseCode = "404",
					description = "Host organization UUID was invalid",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "400",
					description = "Provided org UUID(s) was/were invalid",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
					description = "Not Authorized - do not have privilege to edit this field",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorizeOrganizationEdit
	@DeleteMapping({"${api-prefix.v1}/organization/{id}/subordinates", "${api-prefix.v2}/organization/{id}/subordinates"})
	public ResponseEntity<Object> removeSubordinateOrganization(@Parameter(description = "UUID of the host organization record", required = true) @PathVariable UUID id,
																@Parameter(description = "UUID(s) of subordinate organizations", required = true) @RequestBody List<UUID> orgIds) {

		return new ResponseEntity<>(organizationService.removeSubordinateOrg(id, orgIds), HttpStatus.OK);
	}

	/**
	 * @deprecated No longer valid. Use JSON patch instead. See {@link #jsonPatchPerson(UUID, JsonPatch)}
	 * @param organizationId
	 * @param attribs
	 * @return
	 */
	@Operation(summary = "Updates an existing organization's attributes", description = "Updates an existing organization's attributes")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "203",
					description = "Successful - Entity Field Authority denied access to some fields",
					content = @Content(schema = @Schema(implementation = OrganizationDto.class))),
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
	@Deprecated(since = "v2")
	@PreAuthorizeOrganizationEdit
	@PatchMapping({"${api-prefix.v1}/organization/{id}"})
	public ResponseEntity<OrganizationDto> patchOrganization(
			@Parameter(description = "Organization ID to update", required = true) @PathVariable("id") UUID organizationId,
			@Parameter(description = "Object hash containing the keys to modify (set fields to null to clear that field)", required = true) @RequestBody Map<String, String> attribs) {

			return new ResponseEntity<>(organizationService.modify(organizationId, attribs), HttpStatus.OK);
	}

	/**
	 * @deprecated No longer valid T166. See {@link #addNewOrganizationsWrapped(List)} for new usage.
	 * @param orgs
	 * @return
	 */
	@Operation(summary = "Adds one or more organization entities",
			description = "Adds one or more organization entities - returns that same array of input organizations with their assigned UUIDs. " +
					"If the request does NOT return 201 (Created) because of an error the entire operation is rolled back having persisted nothing. " +
					"The return error message will list the offending UUID or other data that caused the error.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "203",
					description = "Successful - Entity Field Authority denied access to some fields",
					content = @Content(schema = @Schema(implementation = OrganizationDto.class))),
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
	@PreAuthorizeOrganizationCreate
	@Deprecated(since = "v2")
	@PostMapping({"${api-prefix.v1}/organization/organizations"})
	public ResponseEntity<Object> addNewOrganizations(
			@RequestBody List<OrganizationDto> orgs) {
		return new ResponseEntity<>(organizationService.bulkAddOrgs(orgs), HttpStatus.CREATED);
	}
	
	@Operation(summary = "Adds one or more organization entities",
			description = "Adds one or more organization entities - returns that same array of input organizations with their assigned UUIDs. " +
					"If the request does NOT return 201 (Created) because of an error (see other return codes), then " +
					"any new organizations up to that organization that caused the failure will have been committed (but none thereafter)" +
					"The return error message will list the offending UUID or other data that caused the error.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "203",
					description = "Successful - Entity Field Authority denied access to some fields",
					content = @Content(schema = @Schema(implementation = OrganizationDtoResponseWrapper.class))),
			@ApiResponse(responseCode = "201",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = OrganizationDtoResponseWrapper.class))),
			@ApiResponse(responseCode = "400",
					description = "Bad data or validation error",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "409",
					description = "Bad Request / One of the supplied organizations contained a UUID that already exists or other duplicate data",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@WrappedEnvelopeResponse
	@PreAuthorizeOrganizationCreate
	@PostMapping({"${api-prefix.v2}/organization/organizations"})
	public ResponseEntity<Object> addNewOrganizationsWrapped(
			@RequestBody List<OrganizationDto> orgs) {
		return new ResponseEntity<>(organizationService.bulkAddOrgs(orgs), HttpStatus.CREATED);
	}

	@Operation(
			summary = "Patches an existing organization", 
			description = "Patches an existing organization. Fields (subordinateOrganizations and members) cannot be modified through this operation."
	)
	@ApiResponses(value = {
			@ApiResponse(responseCode = "203",
					description = "Successful - Entity Field Authority denied access to some fields",
					content = @Content(schema = @Schema(implementation = OrganizationDto.class))),
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = OrganizationDto.class))),
			@ApiResponse(responseCode = "400",
					description = "Bad request",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "404",
					description = "Resource not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorizeOrganizationEdit
	@PatchMapping(value = {"${api-prefix.v2}/organization/{id}"}, consumes = "application/json-patch+json")
	public ResponseEntity<OrganizationDto> jsonPatchOrganization(
			@Parameter(description = "Organization ID to patch", required = true) @PathVariable("id") UUID orgId,
			@io.swagger.v3.oas.annotations.parameters.RequestBody(
					content = @Content(array = @ArraySchema(
									schema = @Schema( 
									anyOf = {JsonPatchStringArrayValue.class, JsonPatchStringValue.class,
											JsonPatchObjectValue.class, JsonPatchObjectArrayValue.class}))), 
					required = true)
			@RequestBody JsonPatch patch) throws MethodArgumentNotValidException {
		OrganizationDto organizationDto = organizationService.patchOrganization(orgId, patch);
		return new ResponseEntity<>(organizationDto, HttpStatus.OK);
	}

	// helper to build the options map for customization of return DTOs
	private Map<String, String> initCustomizationOptions(String peopleFields, String orgFields) {
		// we have some customizations specified from the user...
		Map<String, String> fields = new HashMap<>();
		fields.put(OrganizationController.PEOPLE_PARAMS_FIELD, peopleFields);
		fields.put(OrganizationController.ORGS_PARAMS_FIELD, orgFields);
		return fields;
	}
	
	@Operation(summary = "Retrieves organizations filtered", description = "Retrieves filtered list of organizations")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
					description = "Successful operation", 
					content = @Content(schema = @Schema(implementation = OrganizationDtoPaginationResponseWrapper.class)),
					headers = @Header(
							name="link",
							description = "Contains the appropriate pagination links if application. "
									+ "If no pagination query params given, then no pagination links will exist. "
									+ "Possible rel values include: first, last, prev, next",
							schema = @Schema(type = "string"))),
			@ApiResponse(responseCode = "400",
					description = "Bad request - most likely bad field or value given",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@WrappedEnvelopeResponse
	@PreAuthorizeOrganizationRead
	@PostMapping(
			value = {"${api-prefix.v2}/organization/filter"},
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE
	)
	public ResponseEntity<Page<OrganizationDto>> filterOrganizations(
            @Parameter(description = "The conditions used to filter", required = true, content = @Content(schema = @Schema(implementation = FilterDto.class)))
				@Valid @RequestBody FilterDto filter,
                @ParameterObject Pageable page) {
		
		Page<OrganizationDto> results = organizationService.getOrganizationsPageSpec(filter.getFilterCriteria(), page);
		
		return new ResponseEntity<>(results, HttpStatus.OK);
	}
}

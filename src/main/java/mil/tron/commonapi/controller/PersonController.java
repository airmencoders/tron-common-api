package mil.tron.commonapi.controller;

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
import mil.tron.commonapi.annotation.security.PreAuthorizePersonCreate;
import mil.tron.commonapi.annotation.security.PreAuthorizePersonDelete;
import mil.tron.commonapi.annotation.security.PreAuthorizePersonEdit;
import mil.tron.commonapi.annotation.security.PreAuthorizePersonRead;
import mil.tron.commonapi.dto.*;
import mil.tron.commonapi.dto.annotation.helper.JsonPatchObjectArrayValue;
import mil.tron.commonapi.dto.annotation.helper.JsonPatchObjectValue;
import mil.tron.commonapi.dto.annotation.helper.JsonPatchStringArrayValue;
import mil.tron.commonapi.dto.annotation.helper.JsonPatchStringValue;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.exception.ExceptionResponse;
import mil.tron.commonapi.service.PersonConversionOptions;
import mil.tron.commonapi.service.PersonFindType;
import mil.tron.commonapi.service.PersonService;
import mil.tron.commonapi.service.UserInfoService;
import org.springdoc.api.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class PersonController {
	private PersonService personService;
	private UserInfoService userInfoService;

	public PersonController(PersonService personService, UserInfoService userInfoService) {
		this.personService = personService;
		this.userInfoService = userInfoService;
	}
	
	/**
	 * @deprecated No longer acceptable as it returns array json data as top most parent.
	 * @param memberships
	 * @param leaderships
	 * @param page
	 * @return
	 */
	@Operation(summary = "Retrieves all persons", description = "Retrieves all persons")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
					description = "Successful operation", 
						content = @Content(array = @ArraySchema(schema = @Schema(implementation = PersonDto.class))))
	})
	@PreAuthorizePersonRead
	@Deprecated(since="${api-prefix.v2}")
	@GetMapping({"${api-prefix.v1}/person"})
	public ResponseEntity<Object> getPersons(
            @Parameter(name = "memberships", description = "Whether to include this person's organization memberships in the response", required = false)
				@RequestParam(name = "memberships", required = false) boolean memberships,
            @Parameter(name = "leaderships", description = "Whether to include the organization ids this person is the leader of in the response", required = false)
                @RequestParam(name = "leaderships", required = false) boolean leaderships,
                @ParameterObject Pageable page) {

		return new ResponseEntity<>(personService.getPersons(PersonConversionOptions.builder().membershipsIncluded(memberships).leadershipsIncluded(leaderships).build(), page), HttpStatus.OK);
	}
	
	@Operation(summary = "Retrieves all persons", description = "Retrieves all persons  with pagination information")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
					description = "Successful operation", 
					content = @Content(schema = @Schema(implementation = PersonDtoPaginationResponseWrapper.class)),
					headers = @Header(
							name="link",
							description = "Contains the appropriate pagination links if application. "
									+ "If no pagination query params given, then no pagination links will exist. "
									+ "Possible rel values include: first, last, prev, next",
							schema = @Schema(type = "string")))
	})
	@PreAuthorizePersonRead
	@WrappedEnvelopeResponse
	@GetMapping({"${api-prefix.v2}/person"})
	public ResponseEntity<Object> getPersonsWrapped(
            @Parameter(name = "memberships", description = "Whether to include this person's organization memberships in the response", required = false)
				@RequestParam(name = "memberships", required = false) boolean memberships,
            @Parameter(name = "leaderships", description = "Whether to include the organization ids this person is the leader of in the response", required = false)
                @RequestParam(name = "leaderships", required = false) boolean leaderships,
                @ParameterObject Pageable page) {
		
		return new ResponseEntity<>(personService
				.getPersonsPage(PersonConversionOptions
						.builder().membershipsIncluded(memberships).leadershipsIncluded(leaderships).build(), page),
				HttpStatus.OK);
	}
	
	@Operation(summary = "Retrieves a person by ID", description = "Retrieves a person by ID")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = PersonDto.class))),
			@ApiResponse(responseCode = "404",
					description = "Resource not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "400",
					description = "Bad request",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorizePersonRead
	@GetMapping(value = {"${api-prefix.v1}/person/{id}", "${api-prefix.v2}/person/{id}"})
	public ResponseEntity<PersonDto> getPerson(
			@Parameter(description = "Person ID to retrieve", required = true) @PathVariable("id") UUID personId,
            @Parameter(name = "memberships", description = "Whether to include this person's organization memberships in the response", required = false)
				@RequestParam(name = "memberships", required = false) boolean memberships,
            @Parameter(name = "leaderships", description = "Whether to include the organization ids this person is the leader of in the response", required = false)
                @RequestParam(name = "leaderships", required = false) boolean leaderships) {

		PersonDto person = personService.getPersonDto(personId, PersonConversionOptions.builder().membershipsIncluded(memberships).leadershipsIncluded(leaderships).build());
		return new ResponseEntity<>(person, HttpStatus.OK);
	}
	
	
	
	/**
	 * @deprecated Method no longer valid to fulfill T219 / CWE-598. {@link #findPersonBy(boolean, boolean, PersonFindDto)} for the new usage.
	 * @param memberships
	 * @param leaderships
	 * @param findByField
	 * @param value
	 * @return
	 */
	@Operation(summary = "Retrieves a person by email or dodid", description = "Retrieves a person using a single identifying property.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = PersonDto.class))),
			@ApiResponse(responseCode = "404",
					description = "Resource not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "400",
					description = "Bad request",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@Parameter(name = "memberships", description = "Whether to include this person's organization memberships in the response", required = false)
	@Parameter(name = "leaderships", description = "Whether to include the organization ids this person is the leader of in the response", required = false)
	@Parameter(name = "findByField", 
				description = "The field to search for", 
				required = true,
				content= @Content(schema = @Schema(implementation = PersonFindType.class)))
	@Parameter(name = "value", description = "The value to search against", required = true)
	@Deprecated(since = "v2")
	@PreAuthorizePersonRead
	@GetMapping(value = {"${api-prefix.v1}/person/find"})
	public ResponseEntity<PersonDto> findPersonBy(
				@RequestParam(name = "memberships", required = false) boolean memberships,
                @RequestParam(name = "leaderships", required = false) boolean leaderships,
                @RequestParam(name = "findByField", required = true) String findByField,
                @RequestParam(name = "value", required = true) String value) {
		
		PersonFindType filter = null;
		try {
			filter = PersonFindType.valueOf(findByField.toUpperCase());
		} catch (Exception ex) {
			throw new BadRequestException(String.format("findByField: %s is invalid.", findByField));
		}

		Person person = personService.getPersonFilter(filter, value);
		PersonDto dto = personService.convertToDto(person, PersonConversionOptions.builder().membershipsIncluded(memberships).leadershipsIncluded(leaderships).build());
		
		return new ResponseEntity<>(dto, HttpStatus.OK);
	}
	
	@Operation(summary = "Retrieves a person by email or dodid", description = "Retrieves a person using a single identifying property.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = PersonDto.class))),
			@ApiResponse(responseCode = "404",
					description = "Resource not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "400",
					description = "Bad request",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorizePersonRead
	@PostMapping(
			value = {"${api-prefix.v2}/person/find"},
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE
	)
	public ResponseEntity<PersonDto> findPersonBy(
			@Parameter(name = "memberships", description = "Whether to include this person's organization memberships in the response", required = false)
				@RequestParam(name = "memberships", required = false) boolean memberships,
			@Parameter(name = "leaderships", description = "Whether to include the organization ids this person is the leader of in the response", required = false)
                @RequestParam(name = "leaderships", required = false) boolean leaderships,
            @Parameter(description = "The information to find a person by", required = true, schema = @Schema(implementation = PersonFindDto.class))
                @Valid @RequestBody PersonFindDto personFindDto) {
		
		Person person = personService.getPersonFilter(personFindDto.getFindType(), personFindDto.getValue());
		PersonDto dto = personService.convertToDto(person, PersonConversionOptions.builder().membershipsIncluded(memberships).leadershipsIncluded(leaderships).build());
		
		return new ResponseEntity<>(dto, HttpStatus.OK);
	}
	
	@Operation(summary = "Adds a person", description = "Adds a person.  Query Ranks controller for available Ranks and Branches. " +
		"If a given Rank or Branch is invalid, the Person will be created with rank 'Unknown' and branch 'Other'")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "203",
					description = "Successful - Entity Field Authority denied access to some fields",
					content = @Content(schema = @Schema(implementation = PersonDto.class))),
			@ApiResponse(responseCode = "201",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = PersonDto.class))),
			@ApiResponse(responseCode = "409",
					description = "Resource already exists with the id provided",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "400",
					description = "Bad request",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorizePersonCreate
	@PostMapping({"${api-prefix.v1}/person", "${api-prefix.v2}/person"})
	public ResponseEntity<PersonDto> createPerson(
			HttpServletResponse response,
			@Parameter(description = "Person to create",
				required = true,
				schema = @Schema(implementation = PersonDto.class)) @Valid @RequestBody PersonDto person) {
		return new ResponseEntity<>(personService.createPerson(person),
				(HttpStatus.valueOf(response.getStatus()) == HttpStatus.NON_AUTHORITATIVE_INFORMATION) ?
						HttpStatus.NON_AUTHORITATIVE_INFORMATION : HttpStatus.CREATED);
	}

	@Operation(summary = "Adds a person using info from P1 JWT")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "203",
					description = "Successful - Entity Field Authority denied access to some fields",
					content = @Content(schema = @Schema(implementation = PersonDto.class))),
			@ApiResponse(responseCode = "201",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = PersonDto.class))),
			@ApiResponse(responseCode = "409",
					description = "Resource already exists with the id provided",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "400",
					description = "Bad request",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorizePersonCreate
	@PostMapping({"${api-prefix.v1}/person/person-jwt", "${api-prefix.v2}/person/person-jwt"})
	public ResponseEntity<PersonDto> createPersonFromJwt(
			HttpServletResponse response,
			@Parameter(description = "Person to create",
				required = true,
				schema = @Schema(implementation = PlatformJwtDto.class)) @Valid @RequestBody PlatformJwtDto person) {
		return new ResponseEntity<>(personService.createPersonFromJwt(person),
				(HttpStatus.valueOf(response.getStatus()) == HttpStatus.NON_AUTHORITATIVE_INFORMATION) ?
						HttpStatus.NON_AUTHORITATIVE_INFORMATION : HttpStatus.CREATED);
	}


	@Operation(summary = "Updates an existing person", description = "Updates an existing person")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "203",
					description = "Successful - Entity Field Authority denied access to some fields",
					content = @Content(schema = @Schema(implementation = PersonDto.class))),
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = PersonDto.class))),
			@ApiResponse(responseCode = "404",
					description = "Resource not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorizePersonEdit
	@PutMapping(value = {"${api-prefix.v1}/person/{id}", "${api-prefix.v2}/person/{id}"})
	public ResponseEntity<Object> updatePerson(
			HttpServletResponse response,
			@Parameter(description = "Person ID to update", required = true) @PathVariable("id") UUID personId,
			@Parameter(description = "Updated person", 
				required = true, 
				schema = @Schema(implementation = PersonDto.class))
				@Valid @RequestBody PersonDto person) {

		PersonDto updatedPerson = personService.updatePerson(personId, person);
		return new ResponseEntity<>(updatedPerson, HttpStatus.valueOf(response.getStatus()));
	}

	@Operation(summary = "Allows a Person to update their own existing record.", 
			description = "The email from the updated Person record must match the email in the authenticated user's JWT,"
					+ " otherwise this action will be rejected.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = PersonDto.class))),
			@ApiResponse(responseCode = "404",
					description = "Resource not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "403",
					description = "Forbidden (Mismatch in email between updated Person record and user's JWT)",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PutMapping(value = {"${api-prefix.v1}/person/self/{id}", "${api-prefix.v2}/person/self/{id}"})
	public ResponseEntity<Object> selfUpdatePerson(@RequestHeader Map<String, String> headers,
			@Parameter(description = "Person ID to update", required = true) @PathVariable("id") UUID personId,
			@Parameter(description = "Updated person", 
				required = true, 
				schema = @Schema(implementation = PersonDto.class))
				@Valid @RequestBody PersonDto person) {
		return selfUpdate(headers.get("authorization"), personId, person);
	}

	private ResponseEntity<Object> selfUpdate(String authHeader, UUID personId, PersonDto person) {
		UserInfoDto userInfo = userInfoService.extractUserInfoFromHeader(authHeader);

		if (userInfo.getEmail().equalsIgnoreCase(person.getEmail())) {
		  PersonDto updatedPerson = personService.updatePerson(personId, person);
		  return new ResponseEntity<>(updatedPerson, HttpStatus.OK);
		} else {
		  throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is forbidden from performing this action.");
		}
	}

	@Operation(summary = "Patches an existing person", description = "Patches an existing person")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "203",
					description = "Successful - Entity Field Authority denied access to some fields",
					content = @Content(schema = @Schema(implementation = PersonDto.class))),
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = PersonDto.class))),
			@ApiResponse(responseCode = "400",
					description = "Bad request",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "404",
					description = "Resource not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorizePersonEdit
	@PatchMapping(path = {"${api-prefix.v1}/person/{id}", "${api-prefix.v2}/person/{id}"}, consumes = "application/json-patch+json")
	public ResponseEntity<PersonDto> patchPerson(
			HttpServletResponse response,
			@Parameter(description = "Person ID to patch", required = true) @PathVariable("id") UUID personId,
			@Parameter(description = "Patched person",
					required = true,
					schema = @Schema(example="[ {'op':'add','path':'/hello','value':'world'} ]",
							oneOf = {JsonPatchStringArrayValue.class, JsonPatchStringValue.class,
									JsonPatchObjectValue.class, JsonPatchObjectArrayValue.class}))
			@RequestBody JsonPatch patch) {
		PersonDto updatedPerson = personService.patchPerson(personId, patch);
		return new ResponseEntity<>(updatedPerson, HttpStatus.valueOf(response.getStatus()));
	}


	@Operation(summary = "Deletes an existing person", description = "Deletes an existing person")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204",
					description = "Successful operation",
					content = @Content),
			@ApiResponse(responseCode = "404",
				description = "Resource not found",
				content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorizePersonDelete
	@DeleteMapping(value = {"${api-prefix.v1}/person/{id}", "${api-prefix.v2}/person/{id}"})
	public ResponseEntity<Object> deletePerson(
			@Parameter(description = "Person ID to delete", required = true) @PathVariable("id") UUID personId) {
		personService.deletePerson(personId);
		return new ResponseEntity<>(HttpStatus.NO_CONTENT);
	}

	/**
	 * @deprecated No longer valid T166. See {@link #addPersonsWrapped(List)} for new usage.
	 * @param people
	 * @return
	 */
	@Operation(summary = "Add one or more members to the database",
			description = "Adds one or more person entities - returns that same array of input persons with their assigned UUIDs. " +
					"If the request does NOT return 201 (Created) because of an error (see other return codes), then " +
					"no new persons will have been committed to the database (if one entity fails, the entire operation fails). " +
					"The return error message will list the offending UUID or other data that caused the error.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "203",
					description = "Successful - Entity Field Authority denied access to some fields",
					content = @Content(schema = @Schema(implementation = PersonDto.class))),
			@ApiResponse(responseCode = "201",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = PersonDto.class))),
			@ApiResponse(responseCode = "400",
					description = "Bad data or validation error",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "409",
					description = "A person already exists with the id provided",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@Deprecated(since = "v2")
	@PreAuthorizePersonCreate
	@PostMapping({"${api-prefix.v1}/person/persons"})
	public ResponseEntity<Object> addPersons(
			HttpServletResponse response,
			@Parameter(description = "Array of persons to add", required = true) @RequestBody List<PersonDto> people) {

		return new ResponseEntity<>(personService.bulkAddPeople(people),
				(HttpStatus.valueOf(response.getStatus()) == HttpStatus.NON_AUTHORITATIVE_INFORMATION) ?
						HttpStatus.NON_AUTHORITATIVE_INFORMATION : HttpStatus.CREATED);
	}
	
	@Operation(summary = "Add one or more members to the database",
			description = "Adds one or more person entities - returns that same array of input persons with their assigned UUIDs. " +
					"If the request does NOT return 201 (Created) because of an error (see other return codes), then " +
					"no new persons will have been committed to the database (if one entity fails, the entire operation fails). " +
					"The return error message will list the offending UUID or other data that caused the error.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "203",
					description = "Successful - Entity Field Authority denied access to some fields",
					content = @Content(schema = @Schema(implementation = PersonDtoResponseWrapper.class))),
			@ApiResponse(responseCode = "201",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = PersonDtoResponseWrapper.class))),
			@ApiResponse(responseCode = "400",
					description = "Bad data or validation error",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "409",
					description = "A person already exists with the id provided",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@WrappedEnvelopeResponse
	@PreAuthorizePersonCreate
	@PostMapping({"${api-prefix.v2}/person/persons"})
	public ResponseEntity<Object> addPersonsWrapped(
			HttpServletResponse response,
			@Parameter(description = "Array of persons to add", required = true) @RequestBody List<PersonDto> people) {

		return new ResponseEntity<>(personService.bulkAddPeople(people),
				(HttpStatus.valueOf(response.getStatus()) == HttpStatus.NON_AUTHORITATIVE_INFORMATION) ?
						HttpStatus.NON_AUTHORITATIVE_INFORMATION : HttpStatus.CREATED);
	}
	
	@Operation(summary = "Retrieves persons filtered", description = "Retrieves filtered list of persons")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
					description = "Successful operation", 
					content = @Content(schema = @Schema(implementation = PersonDtoPaginationResponseWrapper.class)),
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
	@PreAuthorizePersonRead
	@PostMapping(
			value = {"${api-prefix.v2}/person/filter"},
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE
	)
	public ResponseEntity<Page<PersonDto>> filterPerson(
			@Parameter(name = "memberships", description = "Whether to include this person's organization memberships in the response", required = false)
				@RequestParam(name = "memberships", required = false) boolean memberships,
			@Parameter(name = "leaderships", description = "Whether to include the organization ids this person is the leader of in the response", required = false)
                @RequestParam(name = "leaderships", required = false) boolean leaderships,
            @Parameter(description = "The conditions used to filter", required = true, content = @Content(schema = @Schema(implementation = FilterDto.class)))
				@Valid @RequestBody FilterDto filter,
                @ParameterObject Pageable page) {
		Page<PersonDto> results = personService.getPersonsPageSpec(PersonConversionOptions.builder().membershipsIncluded(memberships).leadershipsIncluded(leaderships).build(), filter.getFilterCriteria(), page);
		
		return new ResponseEntity<>(results, HttpStatus.OK);
	}
}

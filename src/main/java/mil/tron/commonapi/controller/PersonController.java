package mil.tron.commonapi.controller;

import com.github.fge.jsonpatch.JsonPatch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.annotation.security.PreAuthorizeRead;
import mil.tron.commonapi.annotation.security.PreAuthorizeWrite;
import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.dto.UserInfoDto;
import mil.tron.commonapi.dto.annotation.helper.JsonPatchObjectArrayValue;
import mil.tron.commonapi.dto.annotation.helper.JsonPatchStringArrayValue;
import mil.tron.commonapi.dto.annotation.helper.JsonPatchObjectValue;
import mil.tron.commonapi.dto.annotation.helper.JsonPatchStringValue;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.exception.ExceptionResponse;
import mil.tron.commonapi.service.PersonConversionOptions;
import mil.tron.commonapi.service.PersonFilterType;
import mil.tron.commonapi.service.PersonService;
import mil.tron.commonapi.service.UserInfoService;

import org.springdoc.api.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("${api-prefix.v1}/person")
public class PersonController {
	private PersonService personService;
	private UserInfoService userInfoService;

	public PersonController(PersonService personService, UserInfoService userInfoService) {
		this.personService = personService;
		this.userInfoService = userInfoService;
	}
	
	@Operation(summary = "Retrieves all persons", description = "Retrieves all persons")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
					description = "Successful operation", 
						content = @Content(array = @ArraySchema(schema = @Schema(implementation = PersonDto.class))))
	})
	@PreAuthorizeRead
	@GetMapping
	public ResponseEntity<Object> getPersons(
            @Parameter(name = "memberships", description = "Whether to include this person's organization memberships in the response", required = false)
				@RequestParam(name = "memberships", required = false) boolean memberships,
            @Parameter(name = "leaderships", description = "Whether to include the organization ids this person is the leader of in the response", required = false)
                @RequestParam(name = "leaderships", required = false) boolean leaderships,
                @ParameterObject Pageable page) {

		return new ResponseEntity<>(personService.getPersons(PersonConversionOptions.builder().membershipsIncluded(memberships).leadershipsIncluded(leaderships).build(), page), HttpStatus.OK);
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
	@PreAuthorizeRead
	@GetMapping(value = "/{id}")
	public ResponseEntity<PersonDto> getPerson(
			@Parameter(description = "Person ID to retrieve", required = true) @PathVariable("id") UUID personId,
            @Parameter(name = "memberships", description = "Whether to include this person's organization memberships in the response", required = false)
				@RequestParam(name = "memberships", required = false) boolean memberships,
            @Parameter(name = "leaderships", description = "Whether to include the organization ids this person is the leader of in the response", required = false)
                @RequestParam(name = "leaderships", required = false) boolean leaderships) {

		PersonDto person = personService.getPersonDto(personId, PersonConversionOptions.builder().membershipsIncluded(memberships).leadershipsIncluded(leaderships).build());
		return new ResponseEntity<>(person, HttpStatus.OK);
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
	@Parameter(name = "memberships", description = "Whether to include this person's organization memberships in the response", required = false)
	@Parameter(name = "leaderships", description = "Whether to include the organization ids this person is the leader of in the response", required = false)
	@Parameter(name = "findByField", 
				description = "The field to search for", 
				required = true,
				content= @Content(schema = @Schema(implementation = PersonFilterType.class)))
	@Parameter(name = "value", description = "The value to search against", required = true)
	@PreAuthorizeRead
	@GetMapping(value = "/find")
	public ResponseEntity<PersonDto> findPersonBy(
				@RequestParam(name = "memberships", required = false) boolean memberships,
                @RequestParam(name = "leaderships", required = false) boolean leaderships,
                @RequestParam(name = "findByField", required = true) String findByField,
                @RequestParam(name = "value", required = true) String value) {
		
		PersonFilterType filter = null;
		try {
			filter = PersonFilterType.valueOf(findByField.toUpperCase());
		} catch (Exception ex) {
			throw new BadRequestException(String.format("findByField: %s is invalid.", findByField));
		}

		Person person = personService.getPersonFilter(filter, value);
		PersonDto dto = personService.convertToDto(person, PersonConversionOptions.builder().membershipsIncluded(memberships).leadershipsIncluded(leaderships).build());
		
		return new ResponseEntity<>(dto, HttpStatus.OK);
	}
	
	@Operation(summary = "Adds a person", description = "Adds a person.  Query Ranks controller for available Ranks and Branches. " +
		"If a given Rank or Branch is invalid, the Person will be created with rank 'Unknown' and branch 'Other'")
	@ApiResponses(value = {
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
	@PreAuthorizeWrite
	@PostMapping
	public ResponseEntity<PersonDto> createPerson(@Parameter(description = "Person to create",
		required = true,
		schema = @Schema(implementation = PersonDto.class)) 
		@Valid @RequestBody PersonDto person) {
		return new ResponseEntity<>(personService.createPerson(person), HttpStatus.CREATED);
	}

	@Operation(summary = "Updates an existing person", description = "Updates an existing person")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = PersonDto.class))),
			@ApiResponse(responseCode = "404",
					description = "Resource not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorizeWrite
	@PutMapping(value = "/{id}")
	public ResponseEntity<Object> updatePerson(
			@Parameter(description = "Person ID to update", required = true) @PathVariable("id") UUID personId,
			@Parameter(description = "Updated person", 
				required = true, 
				schema = @Schema(implementation = PersonDto.class))
				@Valid @RequestBody PersonDto person) {

		PersonDto updatedPerson = personService.updatePerson(personId, person);
		return new ResponseEntity<>(updatedPerson, HttpStatus.OK);
	}

	@Operation(summary = "Updates an existing person", description = "Updates an existing person")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = PersonDto.class))),
			@ApiResponse(responseCode = "404",
					description = "Resource not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PreAuthorizeWrite
	@PutMapping(value = "/self/{id}")
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
		  throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authorized to perform this action.");
		}
	}

	@Operation(summary = "Patches an existing person", description = "Patches an existing person")
	@ApiResponses(value = {
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
	@PreAuthorizeWrite
	@PatchMapping(path = "/{id}", consumes = "application/json-patch+json")
	public ResponseEntity<PersonDto> patchPerson(
			@Parameter(description = "Person ID to patch", required = true) @PathVariable("id") UUID personId,
			@Parameter(description = "Patched person",
					required = true,
					schema = @Schema(example="[ {'op':'add','path':'/hello','value':'world'} ]",
							oneOf = {JsonPatchStringArrayValue.class, JsonPatchStringValue.class,
									JsonPatchObjectValue.class, JsonPatchObjectArrayValue.class}))
			@RequestBody JsonPatch patch) {
		PersonDto updatedPerson = personService.patchPerson(personId, patch);
		return new ResponseEntity<>(updatedPerson, HttpStatus.OK);
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
	@PreAuthorizeWrite
	@DeleteMapping(value = "/{id}")
	public ResponseEntity<Object> deletePerson(
			@Parameter(description = "Person ID to delete", required = true) @PathVariable("id") UUID personId) {
		personService.deletePerson(personId);
		return new ResponseEntity<>(HttpStatus.NO_CONTENT);
	}

	@Operation(summary = "Add one or more members to the database",
			description = "Adds one or more person entities - returns that same array of input persons with their assigned UUIDs. " +
					"If the request does NOT return 201 (Created) because of an error (see other return codes), then " +
					"no new persons will have been committed to the database (if one entity fails, the entire operation fails). " +
					"The return error message will list the offending UUID or other data that caused the error.")
	@ApiResponses(value = {
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
	@PreAuthorizeWrite
	@PostMapping("/persons")
	public ResponseEntity<Object> addPersons(
			@Parameter(description = "Array of persons to add", required = true) @RequestBody List<PersonDto> people) {

		return new ResponseEntity<>(personService.bulkAddPeople(people), HttpStatus.CREATED);
	}

}

package mil.tron.commonapi.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.exception.ExceptionResponse;
import mil.tron.commonapi.pagination.Paginator;
import mil.tron.commonapi.service.PersonService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("${api-prefix.v1}/person")
public class PersonController {
	private PersonService personService;
	private Paginator pager;

	public PersonController(PersonService personService, Paginator pager) {
		this.personService = personService;
		this.pager = pager;
	}
	
	@Operation(summary = "Retrieves all persons", description = "Retrieves all persons")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
					description = "Successful operation", 
						content = @Content(array = @ArraySchema(schema = @Schema(implementation = Person.class))))
	})
	@GetMapping
	public ResponseEntity<Object> getPersons(
			@Parameter(name = "page", description = "Page of content to retrieve", required = false)
				@RequestParam(name = "page", required = false, defaultValue = "1") Long pageNumber,
			@Parameter(name = "size", description = "Size of each page", required = false)
				@RequestParam(name = "size", required = false) Long pageSize) {

		return new ResponseEntity<>(pager.paginate(personService.getPersons(), pageNumber, pageSize), HttpStatus.OK);
	}
	
	@Operation(summary = "Retrieves a person by ID", description = "Retrieves a person by ID")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = Person.class))),
			@ApiResponse(responseCode = "404",
					description = "Resource not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "400",
					description = "Bad request",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@GetMapping(value = "/{id}")
	public ResponseEntity<Person> getPerson(
			@Parameter(description = "Person ID to retrieve", required = true) @PathVariable("id") UUID personId) {
		
		Person person = personService.getPerson(personId);
		return new ResponseEntity<>(person, HttpStatus.OK);
	}
	
	@Operation(summary = "Adds a person", description = "Adds a person")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "201",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = Person.class))),
			@ApiResponse(responseCode = "409",
					description = "Resource already exists with the id provided",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "400",
					description = "Bad request",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PostMapping
	public ResponseEntity<Person> createPerson(@Parameter(description = "Person to create", required = true) @Valid @RequestBody Person person) {
		return new ResponseEntity<>(personService.createPerson(person), HttpStatus.CREATED);
	}
	
	@Operation(summary = "Updates an existing person", description = "Updates an existing person")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = Person.class))),
			@ApiResponse(responseCode = "404",
					description = "Resource not found",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PutMapping(value = "/{id}")
	public ResponseEntity<Person> updatePerson(
			@Parameter(description = "Person ID to update", required = true) @PathVariable("id") UUID personId,
			@Parameter(description = "Updated person", required = true) @Valid @RequestBody Person person) {
		
		Person updatedPerson = personService.updatePerson(personId, person);
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
					content = @Content(schema = @Schema(implementation = Person.class))),
			@ApiResponse(responseCode = "400",
					description = "Bad data or validation error",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
			@ApiResponse(responseCode = "409",
					description = "A person already exists with the id provided",
					content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
	})
	@PostMapping("/persons")
	public ResponseEntity<Object> addPersons(
			@Parameter(description = "Array of persons to add", required = true) @RequestBody List<Person> people) {

		return new ResponseEntity<>(personService.bulkAddPeople(people), HttpStatus.CREATED);
	}

}

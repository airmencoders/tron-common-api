package mil.tron.commonapi.controller;

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
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.person.Person;
import mil.tron.commonapi.service.PersonService;

@RestController
@RequestMapping("${api-prefix.v1}/person")
public class PersonController {
	private PersonService personService;

	public PersonController(PersonService personService) {
		this.personService = personService;
	}
	
	@Operation(summary = "Retrieves all persons", description = "Retrieves all persons")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", 
					description = "Successful operation", 
						content = @Content(array = @ArraySchema(schema = @Schema(implementation = Person.class))))
	})
	@GetMapping
	public ResponseEntity<Iterable<Person>> getPersons() {
		return new ResponseEntity<>(personService.getPersons(), HttpStatus.OK);
	}
	
	@Operation(summary = "Retrieves a person by ID", description = "Retrieves a person by ID")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = Person.class))),
			@ApiResponse(responseCode = "404",
					description = "Resource not found",
					content = @Content)
	})
	@GetMapping(value = "/{id}")
	public ResponseEntity<Person> getPerson(
			@Parameter(description = "Person ID to retrieve", required = true) @PathVariable("id") UUID personId) {
		
		Person person = personService.getPerson(personId);
		
		if (person != null)
			return new ResponseEntity<>(person, HttpStatus.OK);
		else
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
	}
	
	@Operation(summary = "Adds a person", description = "Adds a person")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "201",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = Person.class)))
	})
	@PostMapping
	public ResponseEntity<Person> createPerson(@Parameter(description = "Person to create", required = true) @RequestBody Person person) {
		return new ResponseEntity<>(personService.createPerson(person), HttpStatus.CREATED);
	}
	
	@Operation(summary = "Updates an existing person", description = "Updates an existing person")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
					description = "Successful operation",
					content = @Content(schema = @Schema(implementation = Person.class))),
			@ApiResponse(responseCode = "404",
					description = "Resource not found",
					content = @Content)
	})
	@PutMapping(value = "/{id}")
	public ResponseEntity<Person> updatePerson(
			@Parameter(description = "Person ID to update", required = true) @PathVariable("id") UUID personId,
			@Parameter(description = "Updated person", required = true) @RequestBody Person person) {
		
		Person updatedPerson = personService.updatePerson(personId, person);
		
		if (updatedPerson != null)
			return new ResponseEntity<>(updatedPerson, HttpStatus.OK);
		else
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
	}
	
	@Operation(summary = "Deletes an existing person", description = "Deletes an existing person")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204",
					description = "Successful operation",
					content = @Content)
	})
	@DeleteMapping(value = "/{id}")
	public ResponseEntity<Object> deletePerson(
			@Parameter(description = "Person ID to delete", required = true) @PathVariable("id") UUID personId) {
		personService.deletePerson(personId);
		return new ResponseEntity<>(HttpStatus.NO_CONTENT);
	}

}

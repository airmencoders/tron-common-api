package mil.tron.commonapi.service;

import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.PersonRepository;
import mil.tron.commonapi.service.utility.PersonUniqueChecksServiceImpl;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;

@ExtendWith(MockitoExtension.class)
class PersonServiceImplTest {
	@Mock
	private PersonRepository repository;

	@Mock
	private PersonUniqueChecksServiceImpl uniqueChecksService;
	
	@InjectMocks
	private PersonServiceImpl personService;
	
	private Person testPerson;
	private PersonDto testDto;
	
	@BeforeEach
	public void beforeEachSetup() {
		testPerson = new Person();
		testPerson.setId(UUID.randomUUID());
		testPerson.setFirstName("Test");
		testPerson.setLastName("Person");
		testPerson.setEmail("test@good.email");
		testDto = new PersonDto();
		testDto.setId(testPerson.getId());
		testDto.setFirstName(testPerson.getFirstName());
		testDto.setLastName(testPerson.getLastName());
		testDto.setEmail(testPerson.getEmail());
	}

	@Nested
	class CreatePersonTest {
		@Test
	    void successfulCreate() {
	    	// Test successful save
	        Mockito.when(repository.save(Mockito.any(Person.class))).thenReturn(testPerson);
	        Mockito.when(repository.existsById(Mockito.any(UUID.class))).thenReturn(false);
	        Mockito.when(uniqueChecksService.personEmailIsUnique(Mockito.any(Person.class))).thenReturn(true);
	        PersonDto createdPerson = personService.createPerson(testDto);
	        assertThat(createdPerson.getId()).isEqualTo(testPerson.getId());
	    }
		
		@Test
		void idAlreadyExists() {
			// Test id already exists
	        Mockito.when(repository.existsById(Mockito.any(UUID.class))).thenReturn(true);
	        assertThrows(ResourceAlreadyExistsException.class, () -> personService.createPerson(testDto));
		}
		
		@Test
		void emailAlreadyExists() {
			 // Test email already exists
	        Person existingPersonWithEmail = new Person();
	    	existingPersonWithEmail.setEmail(testPerson.getEmail());
	    	
	    	Mockito.when(repository.existsById(Mockito.any(UUID.class))).thenReturn(false);
	    	Mockito.when(uniqueChecksService.personEmailIsUnique(Mockito.any(Person.class))).thenReturn(false);
	    	assertThatExceptionOfType(ResourceAlreadyExistsException.class).isThrownBy(() -> {
	    		personService.createPerson(testDto);
	    	});
		}
	}
	
	@Nested
	class UpdatePersonTest {
		@Test
		void idsNotMatching() {
			// Test id not matching person id
	    	assertThrows(InvalidRecordUpdateRequest.class, () -> personService.updatePerson(UUID.randomUUID(), testDto));
		}
		
		@Test
		void idNotExist() {
			// Test id not exist
	    	Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.ofNullable(null));
	    	assertThrows(RecordNotFoundException.class, () -> personService.updatePerson(testPerson.getId(), testDto));
		}
		
		@Test
		void emailAlreadyExists() {
			// Test updating email to one that already exists in database
	    	PersonDto newPerson = new PersonDto();
	    	newPerson.setId(testPerson.getId());
	    	newPerson.setFirstName(testPerson.getFirstName());
	    	newPerson.setLastName(testPerson.getLastName());
	    	newPerson.setEmail("test@new.person");
	    	UUID testId = newPerson.getId();
	    	
	    	Person existingPersonWithEmail = new Person();
	    	existingPersonWithEmail.setEmail(newPerson.getEmail());
	    	
	    	Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(testPerson));
	    	Mockito.when(uniqueChecksService.personEmailIsUnique(Mockito.any(Person.class))).thenReturn(false);
	    	assertThatExceptionOfType(InvalidRecordUpdateRequest.class).isThrownBy(() -> {
	    		personService.updatePerson(testId, newPerson);
	    	});
		}
		
		@Test
		void successfulUpdate() {
			// Successful update
	    	Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(testPerson));
	    	Mockito.when(uniqueChecksService.personEmailIsUnique(Mockito.any(Person.class))).thenReturn(true);
	    	Mockito.when(repository.save(Mockito.any(Person.class))).thenReturn(testPerson);
	    	PersonDto updatedPerson = personService.updatePerson(testPerson.getId(), testDto);
	    	assertThat(updatedPerson.getId()).isEqualTo(testPerson.getId());
		}
 	}

    @Test
    void deletePersonTest() {
		assertThrows(RecordNotFoundException.class, () -> personService.deletePerson(testPerson.getId()));

		Mockito.when(repository.existsById(Mockito.any(UUID.class))).thenReturn(true);
        personService.deletePerson(testPerson.getId());
        Mockito.verify(repository, Mockito.times(1)).deleteById(testPerson.getId());    
    }

    @Test
    void getPersonsTest() {
    	Mockito.when(repository.findAll()).thenReturn(Arrays.asList(testPerson));
    	Iterable<PersonDto> persons = personService.getPersons();
    	assertThat(persons).hasSize(1);
    }

    @Test
    void getPersonTest() {
    	// Test person exists
    	Mockito.when(repository.findById(testPerson.getId())).thenReturn(Optional.of(testPerson));
    	Person retrievedPerson = personService.getPerson(testPerson.getId());
    	assertThat(retrievedPerson).isEqualTo(testPerson);
    	
    	// Test person not exists
    	Mockito.when(repository.findById(testPerson.getId())).thenReturn(Optional.ofNullable(null));
    	assertThrows(RecordNotFoundException.class, () -> personService.getPerson(testPerson.getId()));
    }

    @Test
	void bulkCreatePersonTest() {
		Mockito.when(repository.save(Mockito.any(Person.class))).then(returnsFirstArg());
		Mockito.when(repository.existsById(Mockito.any(UUID.class))).thenReturn(false);
		Mockito.when(uniqueChecksService.personEmailIsUnique(Mockito.any(Person.class))).thenReturn(true);
		List<PersonDto> people = Lists.newArrayList(
				new PersonDto(),
				new PersonDto(),
				new PersonDto(),
				new PersonDto()
		);

		List<PersonDto> createdPeople = personService.bulkAddPeople(people);
		assertThat(createdPeople).hasSize(4);
	}
    
}

package mil.tron.commonapi.service;

import mil.tron.commonapi.person.Person;
import mil.tron.commonapi.repository.PersonRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class PersonServiceImplTest {
	@Mock
	PersonRepository repository;
	
	@InjectMocks
	PersonServiceImpl personService;
	
	Person testPerson;
	
	@BeforeEach
	public void beforeEachSetup() {
		testPerson = new Person();
		testPerson.setFirstName("Test");
		testPerson.setLastName("Person");
	}

    @Test
    void createPersonTest() {
        Mockito.when(personService.createPerson(Mockito.any(Person.class))).thenReturn(testPerson);
        
        Person createdPerson = personService.createPerson(testPerson);
        assertThat(createdPerson).isEqualTo(testPerson);
    }

    @Test
    void updatePersonTest() {
        // Test id not matching person id
    	Person idNotMatchingPersonId = personService.updatePerson(UUID.randomUUID(), testPerson);
    	assertThat(idNotMatchingPersonId).isNull();
    	
    	// Test id not exist
    	Person idNotExist = personService.updatePerson(testPerson.getId(), testPerson);
    	assertThat(idNotExist).isNull();
    	
    	// Successful update
    	Mockito.when(repository.existsById(Mockito.any(UUID.class))).thenReturn(true);
//    	Mockito.when(repository.save(Mockito.any(Person.class))).thenReturn(testPerson);
    	Mockito.when(personService.updatePerson(testPerson.getId(), testPerson)).thenReturn(testPerson);
    	
    	Person updatedPerson = personService.updatePerson(testPerson.getId(), testPerson);
    	assertThat(updatedPerson).isEqualTo(testPerson);
    }

    @Test
    void deletePersonTest() {
        personService.deletePerson(testPerson.getId());
        
        Mockito.verify(repository, Mockito.times(1)).deleteById(testPerson.getId());    
    }

    @Test
    void getPersonsTest() {
    	Mockito.when(personService.getPersons()).thenReturn(Arrays.asList(testPerson));
    	
    	Iterable<Person> persons = personService.getPersons();
    	assertThat(persons).hasSize(1);
    }

    @Test
    void getPersonTest() {
    	Mockito.when(repository.findById(testPerson.getId())).thenReturn(Optional.of(testPerson));
    	
    	Person retrievedPerson = personService.getPerson(testPerson.getId());
    	assertThat(retrievedPerson).isEqualTo(testPerson);
    }
    
}

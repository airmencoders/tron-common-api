package mil.tron.commonapi.service;

import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.ranks.Rank;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.PersonRepository;
import mil.tron.commonapi.repository.ranks.RankRepository;
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

import java.util.*;

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

	@Mock
	private RankRepository rankRepository;
	
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

	@Nested
	class ConvertToDtoTest {
		@Test
		void noRank() {
			PersonDto dto = personService.convertToDto(Person.builder()
					.firstName("first")
					.build());

			assertThat(dto.getFirstName()).isEqualTo("first");
			assertThat(dto.getRank()).isNull();
			assertThat(dto.getBranch()).isNull();
		}

		@Test
		void rank() {
			PersonDto dto = personService.convertToDto(Person.builder()
					.rank(Rank.builder()
							.abbreviation("Capt")
							.branchType(Branch.USAF)
							.build())
					.build());
			assertThat(dto.getRank()).isEqualTo("Capt");
			assertThat(dto.getBranch()).isEqualTo(Branch.USAF);
		}

		@Test
		void allProperties() {
			Person person = Person.builder()
					.address("adr")
					.admin(true)
					.afsc("sc")
					.approved(true)
					.deros("1/2")
					.dodid("1234567890")
					.dor(new Date())
					.dutyTitle("title")
					.dutyPhone("555")
					.email("a@b.c")
					.etsDate(new Date())
					.firstName("first")
					.fltChief("chf")
					.go81("81")
					.gp("gp")
					.imds("sucks")
					.lastName("last")
					.manNumber("567")
					.middleName("MI")
					.phone("888")
					.ptDate(new Date())
					.rank(Rank.builder()
							.abbreviation("Adm")
							.branchType(Branch.USN)
							.build())
					.title("title")
					.wc("wc")
					.build();

			PersonDto dto = personService.convertToDto(person);

			assertThat(dto.getAddress()).isEqualTo(person.getAddress());
			assertThat(dto.isAdmin()).isEqualTo(person.isAdmin());
			assertThat(dto.getAfsc()).isEqualTo(person.getAfsc());
			assertThat(dto.isApproved()).isEqualTo(person.isApproved());
			assertThat(dto.getDeros()).isEqualTo(person.getDeros());
			assertThat(dto.getDodid()).isEqualTo(person.getDodid());
			assertThat(dto.getDor()).isEqualTo(person.getDor());
			assertThat(dto.getDutyTitle()).isEqualTo(person.getDutyTitle());
			assertThat(dto.getDutyPhone()).isEqualTo(person.getDutyPhone());
			assertThat(dto.getEmail()).isEqualTo(person.getEmail());
			assertThat(dto.getEtsDate()).isEqualTo(person.getEtsDate());
			assertThat(dto.getFirstName()).isEqualTo(person.getFirstName());
			assertThat(dto.getFltChief()).isEqualTo(person.getFltChief());
			assertThat(dto.getGo81()).isEqualTo(person.getGo81());
			assertThat(dto.getGp()).isEqualTo(person.getGp());
			assertThat(dto.getImds()).isEqualTo(person.getImds());
			assertThat(dto.getLastName()).isEqualTo(person.getLastName());
			assertThat(dto.getManNumber()).isEqualTo(person.getManNumber());
			assertThat(dto.getMiddleName()).isEqualTo(person.getMiddleName());
			assertThat(dto.getPhone()).isEqualTo(person.getPhone());
			assertThat(dto.getPtDate()).isEqualTo(person.getPtDate());
			assertThat(dto.getTitle()).isEqualTo(person.getTitle());
			assertThat(dto.getWc()).isEqualTo(person.getWc());
			assertThat(dto.getRank()).isEqualTo(person.getRank().getAbbreviation());
			assertThat(dto.getBranch()).isEqualTo(person.getRank().getBranchType());
		}
	}

	@Nested
	class ConvertToEntityTest {
		@Test
		void noRank() {
			Person person = personService.convertToEntity(PersonDto.builder()
					.firstName("first")
					.build());
			assertThat(person.getFirstName()).isEqualTo("first");
		}

		@Test
		void rank() {
			Rank rank = Rank.builder()
					.abbreviation("Capt")
					.branchType(Branch.USAF).build();
			Mockito.when(rankRepository.findByAbbreviationAndBranchType("Capt", Branch.USAF)).thenReturn(Optional.of(rank));
			Person person = personService.convertToEntity(PersonDto.builder()
					.rank("Capt")
					.branch(Branch.USAF)
					.build());
			assertThat(person.getRank()).isEqualTo(rank);
		}
	}
}

package mil.tron.commonapi.service;

import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.PersonMetadata;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.ranks.Rank;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.PersonMetadataRepository;
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

	@Mock
	private PersonMetadataRepository personMetadataRepository;
	
	@InjectMocks
	private PersonServiceImpl personService;
	
	private Person testPerson;
	private PersonDto testDto;
	
	@BeforeEach
	public void beforeEachSetup() {
		testPerson = Person.builder()
				.address("adr")
				.dodid("1234567890")
				.dutyTitle("title")
				.dutyPhone("555")
				.email("a@b.c")
				.firstName("first")
				.lastName("last")
				.middleName("MI")
				.phone("888")
				.rank(Rank.builder()
						.abbreviation("Capt")
						.branchType(Branch.USAF)
						.build())
				.title("title")
				.build();
		testDto = personService.convertToDto(testPerson);
	}

	@Nested
	class CreatePersonTest {
		@Test
	    void successfulCreate() {
	    	// Test successful save
			Mockito.when(rankRepository.findByAbbreviationAndBranchType(Mockito.any(), Mockito.any())).thenReturn(Optional.of(testPerson.getRank()));
	        Mockito.when(repository.save(Mockito.any(Person.class))).thenReturn(testPerson);
	        Mockito.when(repository.existsById(Mockito.any(UUID.class))).thenReturn(false);
	        Mockito.when(uniqueChecksService.personEmailIsUnique(Mockito.any(Person.class))).thenReturn(true);
	        PersonDto createdPerson = personService.createPerson(testDto);
	        assertThat(createdPerson.getId()).isEqualTo(testPerson.getId());
	    }
		
		@Test
		void idAlreadyExists() {
			// Test id already exists
			Mockito.when(rankRepository.findByAbbreviationAndBranchType(Mockito.any(), Mockito.any())).thenReturn(Optional.of(testPerson.getRank()));
	        Mockito.when(repository.existsById(Mockito.any(UUID.class))).thenReturn(true);
	        assertThrows(ResourceAlreadyExistsException.class, () -> personService.createPerson(testDto));
		}
		
		@Test
		void emailAlreadyExists() {
			 // Test email already exists
	        Person existingPersonWithEmail = new Person();
	    	existingPersonWithEmail.setEmail(testPerson.getEmail());

			Mockito.when(rankRepository.findByAbbreviationAndBranchType(Mockito.any(), Mockito.any())).thenReturn(Optional.of(testPerson.getRank()));
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
			Mockito.when(rankRepository.findByAbbreviationAndBranchType(Mockito.any(), Mockito.any())).thenReturn(Optional.of(testPerson.getRank()));
	    	assertThrows(InvalidRecordUpdateRequest.class, () -> personService.updatePerson(UUID.randomUUID(), testDto));
		}
		
		@Test
		void idNotExist() {
			// Test id not exist
			Mockito.when(rankRepository.findByAbbreviationAndBranchType(Mockito.any(), Mockito.any())).thenReturn(Optional.of(testPerson.getRank()));
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

			Mockito.when(rankRepository.findByAbbreviationAndBranchType(Mockito.any(), Mockito.any())).thenReturn(Optional.of(testPerson.getRank()));
	    	Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(testPerson));
	    	Mockito.when(uniqueChecksService.personEmailIsUnique(Mockito.any(Person.class))).thenReturn(false);
	    	assertThatExceptionOfType(InvalidRecordUpdateRequest.class).isThrownBy(() -> {
	    		personService.updatePerson(testId, newPerson);
	    	});
		}
		
		@Test
		void successfulUpdate() {
			// Successful update
			Mockito.when(rankRepository.findByAbbreviationAndBranchType(Mockito.any(), Mockito.any())).thenReturn(Optional.of(testPerson.getRank()));
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
		Mockito.when(rankRepository.findByAbbreviationAndBranchType(Mockito.any(), Mockito.any())).thenReturn(Optional.of(testPerson.getRank()));
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
			PersonDto dto = personService.convertToDto(testPerson);

			assertThat(dto.getAddress()).isEqualTo(testPerson.getAddress());
			assertThat(dto.getDodid()).isEqualTo(testPerson.getDodid());
			assertThat(dto.getDutyTitle()).isEqualTo(testPerson.getDutyTitle());
			assertThat(dto.getDutyPhone()).isEqualTo(testPerson.getDutyPhone());
			assertThat(dto.getEmail()).isEqualTo(testPerson.getEmail());
			assertThat(dto.getFirstName()).isEqualTo(testPerson.getFirstName());
			assertThat(dto.getLastName()).isEqualTo(testPerson.getLastName());
			assertThat(dto.getMiddleName()).isEqualTo(testPerson.getMiddleName());
			assertThat(dto.getPhone()).isEqualTo(testPerson.getPhone());
			assertThat(dto.getTitle()).isEqualTo(testPerson.getTitle());
			assertThat(dto.getRank()).isEqualTo(testPerson.getRank().getAbbreviation());
			assertThat(dto.getBranch()).isEqualTo(testPerson.getRank().getBranchType());
		}
	}

	@Nested
	class ConvertToEntityTest {
		@Test
		void noRank() {
			assertThrows(RecordNotFoundException.class, () -> personService.convertToEntity(PersonDto.builder()
					.firstName("first")
					.build()));
		}

		@Test
		void rank() {
			Mockito.when(rankRepository.findByAbbreviationAndBranchType(Mockito.any(), Mockito.any())).thenReturn(Optional.of(testPerson.getRank()));
			Person person = personService.convertToEntity(PersonDto.builder()
					.rank("Capt")
					.branch(Branch.USAF)
					.build());
			assertThat(person.getRank()).isEqualTo(testPerson.getRank());
		}
	}

	@Test
	void loadMetadataTest() {
		UUID id = UUID.randomUUID();
		Mockito.when(personMetadataRepository.findAllById(Mockito.any()))
				.thenReturn(List.of(
						new PersonMetadata(id, "prop1", "value1"),
						new PersonMetadata(id, "prop2", "value2")));

		PersonDto dto = PersonDto.builder().id(id).build();

		PersonDto result = personService.loadMetadata(dto, "prop1,prop2");

		assertThat(result.getMetaProperty("prop1")).isEqualTo("value1");
		assertThat(result.getMetaProperty("prop2")).isEqualTo("value2");
	}
}

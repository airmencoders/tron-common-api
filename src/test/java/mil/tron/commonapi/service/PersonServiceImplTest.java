package mil.tron.commonapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.PersonMetadata;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.pubsub.messages.PubSubMessage;
import mil.tron.commonapi.entity.ranks.Rank;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.pubsub.EventManagerServiceImpl;
import mil.tron.commonapi.repository.PersonMetadataRepository;
import mil.tron.commonapi.repository.PersonRepository;
import mil.tron.commonapi.repository.ranks.RankRepository;
import mil.tron.commonapi.service.utility.PersonUniqueChecksServiceImpl;
import org.assertj.core.util.Lists;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
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

	@Mock
	private EventManagerServiceImpl eventManagerService;
	
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
		testPerson.getMetadata().addAll(List.of(
				new PersonMetadata(testPerson.getId(), "afsc", "value1"),
				new PersonMetadata(testPerson.getId(), "admin", "value2")
		));
		testDto = personService.convertToDto(testPerson, null);
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
			Mockito.doNothing().when(eventManagerService).recordEventAndPublish(Mockito.any(PubSubMessage.class));
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

		@Test
		void invalidProperty() {
			testDto.setMetaProperty("blahblah", "value");
			Mockito.when(rankRepository.findByAbbreviationAndBranchType(Mockito.any(), Mockito.any())).thenReturn(Optional.of(testPerson.getRank()));
			Mockito.when(repository.existsById(Mockito.any(UUID.class))).thenReturn(false);
			Mockito.when(uniqueChecksService.personEmailIsUnique(Mockito.any(Person.class))).thenReturn(true);
			assertThatExceptionOfType(InvalidRecordUpdateRequest.class).isThrownBy(() -> {
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
			Mockito.doNothing().when(eventManagerService).recordEventAndPublish(Mockito.any(PubSubMessage.class));
	    	PersonDto updatedPerson = personService.updatePerson(testPerson.getId(), testDto);
	    	assertThat(updatedPerson.getId()).isEqualTo(testPerson.getId());
		}

		@Test
		void invalidProperty() {
			testDto.setMetaProperty("blahblah", "value");
			Mockito.when(rankRepository.findByAbbreviationAndBranchType(Mockito.any(), Mockito.any())).thenReturn(Optional.of(testPerson.getRank()));
			Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(testPerson));
			Mockito.when(uniqueChecksService.personEmailIsUnique(Mockito.any(Person.class))).thenReturn(true);
			assertThatExceptionOfType(InvalidRecordUpdateRequest.class).isThrownBy(() -> {
				personService.updatePerson(testPerson.getId(), testDto);
			});
		}
 	}

	@Nested
	class PatchPersonTest {
		@Test
		void successfulPatch() throws JSONException, IOException {
			JSONObject content = new JSONObject();
			content.put("op","replace");
			content.put("path","/firstName");
			content.put("value",testPerson.getFirstName());
			JSONArray patch = new JSONArray();
			patch.put(content);

			ObjectMapper objectMapper = new ObjectMapper();
			JsonNode newNode = objectMapper.readTree(patch.toString());
			JsonPatch patch2 = JsonPatch.fromJson(newNode);

			Person tempTestPerson = testPerson;
			tempTestPerson.setFirstName("patchFirst");

			// Test id not matching person id
			Mockito.when(repository.findById(Mockito.any())).thenReturn(Optional.of(tempTestPerson));
			Mockito.when(rankRepository.findByAbbreviationAndBranchType(Mockito.any(), Mockito.any())).thenReturn(Optional.of(testPerson.getRank()));
//			assertThrows(InvalidRecordUpdateRequest.class, () -> personService.patchPerson(UUID.randomUUID(), patch2));
			PersonDto patchedPerson = personService.patchPerson(testPerson.getId(), patch2);
			assertThat(patchedPerson.getId()).isEqualTo(testPerson.getId());
		}

//		@Test
//		void idNotExist() {
//			// Test id not exist
//			Mockito.when(rankRepository.findByAbbreviationAndBranchType(Mockito.any(), Mockito.any())).thenReturn(Optional.of(testPerson.getRank()));
//			Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.ofNullable(null));
//			assertThrows(RecordNotFoundException.class, () -> personService.updatePerson(testPerson.getId(), testDto));
//		}
//
//		@Test
//		void emailAlreadyExists() {
//			// Test updating email to one that already exists in database
//			PersonDto newPerson = new PersonDto();
//			newPerson.setId(testPerson.getId());
//			newPerson.setFirstName(testPerson.getFirstName());
//			newPerson.setLastName(testPerson.getLastName());
//			newPerson.setEmail("test@new.person");
//			UUID testId = newPerson.getId();
//
//			Person existingPersonWithEmail = new Person();
//			existingPersonWithEmail.setEmail(newPerson.getEmail());
//
//			Mockito.when(rankRepository.findByAbbreviationAndBranchType(Mockito.any(), Mockito.any())).thenReturn(Optional.of(testPerson.getRank()));
//			Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(testPerson));
//			Mockito.when(uniqueChecksService.personEmailIsUnique(Mockito.any(Person.class))).thenReturn(false);
//			assertThatExceptionOfType(InvalidRecordUpdateRequest.class).isThrownBy(() -> {
//				personService.updatePerson(testId, newPerson);
//			});
//		}
//
//		@Test
//		void successfulUpdate() {
//			// Successful update
//			Mockito.when(rankRepository.findByAbbreviationAndBranchType(Mockito.any(), Mockito.any())).thenReturn(Optional.of(testPerson.getRank()));
//			Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(testPerson));
//			Mockito.when(uniqueChecksService.personEmailIsUnique(Mockito.any(Person.class))).thenReturn(true);
//			Mockito.when(repository.save(Mockito.any(Person.class))).thenReturn(testPerson);
//			Mockito.doNothing().when(eventManagerService).recordEventAndPublish(Mockito.any(PubSubMessage.class));
//			PersonDto updatedPerson = personService.updatePerson(testPerson.getId(), testDto);
//			assertThat(updatedPerson.getId()).isEqualTo(testPerson.getId());
//		}
//
//		@Test
//		void invalidProperty() {
//			testDto.setMetaProperty("blahblah", "value");
//			Mockito.when(rankRepository.findByAbbreviationAndBranchType(Mockito.any(), Mockito.any())).thenReturn(Optional.of(testPerson.getRank()));
//			Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(testPerson));
//			Mockito.when(uniqueChecksService.personEmailIsUnique(Mockito.any(Person.class))).thenReturn(true);
//			assertThatExceptionOfType(InvalidRecordUpdateRequest.class).isThrownBy(() -> {
//				personService.updatePerson(testPerson.getId(), testDto);
//			});
//		}
	}

    @Test
    void deletePersonTest() {
		assertThrows(RecordNotFoundException.class, () -> personService.deletePerson(testPerson.getId()));
		Mockito.doNothing().when(eventManagerService).recordEventAndPublish(Mockito.any(PubSubMessage.class));
		Mockito.when(repository.existsById(Mockito.any(UUID.class))).thenReturn(true);
        personService.deletePerson(testPerson.getId());
        Mockito.verify(repository, Mockito.times(1)).deleteById(testPerson.getId());    
    }

    @Test
    void getPersonsTest() {
    	Mockito.when(repository.findAll()).thenReturn(Arrays.asList(testPerson));
    	Iterable<PersonDto> persons = personService.getPersons(null);
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

		Mockito.doNothing().when(eventManagerService).recordEventAndPublish(Mockito.any(PubSubMessage.class));

		List<PersonDto> createdPeople = personService.bulkAddPeople(people);
		assertThat(createdPeople).hasSize(4);
	}

	@Nested
	class ConvertToDtoTest {
		@Test
		void noRank() {
			PersonDto dto = personService.convertToDto(Person.builder()
					.firstName("first")
					.build(), null);

			assertThat(dto.getFirstName()).isEqualTo("first");
			assertThat(dto.getRank()).isNull();
			assertThat(dto.getBranch()).isNull();
		}

		@Test
		void rank() {
			PersonDto dto = personService.convertToDto(testPerson, null);

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

		@Test
		void metadata() {
			testPerson.getMetadata().add(new PersonMetadata(testPerson.getId(), "prop1", "value1"));
			testPerson.getMetadata().add(new PersonMetadata(testPerson.getId(), "prop2", "value2"));

			PersonDto dto = personService.convertToDto(testPerson, null);

			assertThat(dto.getMetaProperty("prop1")).isEqualTo("value1");
			assertThat(dto.getMetaProperty("prop2")).isEqualTo("value2");
			assertThat(dto.getMetaProperty("prop3")).isNull();
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
}

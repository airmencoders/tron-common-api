package mil.tron.commonapi.service.utility;

import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.repository.PersonRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public class PersonUniqueChecksServiceImplTest {

    @Mock
    private PersonRepository personRepo;

    @InjectMocks
    private PersonUniqueChecksServiceImpl uniqueChecksService;

    @Test
    void testUniqueEmailCheck() {
        Person testPerson = Person.builder().email("test@test.com").build();
        Mockito.when(personRepo.existsById(Mockito.any(UUID.class))).thenReturn(false);
        Mockito.when(personRepo.findByEmailIgnoreCase(Mockito.any(String.class))).thenReturn(Optional.of(testPerson));
        assertFalse(uniqueChecksService.personEmailIsUnique(testPerson));

        // null email is allowed
        testPerson.setEmail(null);
        assertTrue(uniqueChecksService.personEmailIsUnique(testPerson));

        // no dupes found
        testPerson.setEmail("Test@tester.com");
        Mockito.when(personRepo.findByEmailIgnoreCase(Mockito.any(String.class))).thenReturn(Optional.empty());
        assertTrue(uniqueChecksService.personEmailIsUnique(testPerson));

        // switch to existing record (update with a non-unique email)
        Mockito.when(personRepo.existsById(Mockito.any(UUID.class))).thenReturn(true);
        Mockito.when(personRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(new Person()));
        Mockito.when(personRepo.findByEmailIgnoreCase(Mockito.any(String.class))).thenReturn(Optional.of(testPerson));
        assertFalse(uniqueChecksService.personEmailIsUnique(testPerson));

        // existing record (update with a unique email)
        Mockito.when(personRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(testPerson));
        assertTrue(uniqueChecksService.personEmailIsUnique(testPerson));

    }

    @Test
    void testUniqueDodidCheck() {
        Person testPerson = Person.builder().dodid("123456789").build();
        Mockito.when(personRepo.existsById(Mockito.any(UUID.class))).thenReturn(false);
        Mockito.when(personRepo.findByDodidIgnoreCase(Mockito.any(String.class))).thenReturn(Optional.of(testPerson));
        assertFalse(uniqueChecksService.personDodidIsUnique(testPerson));

        // null dodid is allowed
        testPerson.setDodid(null);
        assertTrue(uniqueChecksService.personDodidIsUnique(testPerson));

        // no dupes found
        testPerson.setDodid("999999999");
        Mockito.when(personRepo.findByDodidIgnoreCase(Mockito.any(String.class))).thenReturn(Optional.empty());
        assertTrue(uniqueChecksService.personDodidIsUnique(testPerson));

        // switch to existing record (update with a non-unique dodid)
        Mockito.when(personRepo.existsById(Mockito.any(UUID.class))).thenReturn(true);
        Mockito.when(personRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(new Person()));
        Mockito.when(personRepo.findByDodidIgnoreCase(Mockito.any(String.class))).thenReturn(Optional.of(testPerson));
        assertFalse(uniqueChecksService.personDodidIsUnique(testPerson));

        // existing record (update with a unique dodid)
        Mockito.when(personRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(testPerson));
        assertTrue(uniqueChecksService.personDodidIsUnique(testPerson));

    }
}

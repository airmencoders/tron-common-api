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
}

package mil.tron.commonapi.service;

import mil.tron.commonapi.entity.Airman;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.AirmanRepository;
import mil.tron.commonapi.service.utility.PersonUniqueChecksServiceImpl;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Mockito.doNothing;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class AirmanServiceImplTest {

    @InjectMocks
    AirmanServiceImpl airmanService;

    @Mock
    PersonUniqueChecksServiceImpl uniqueChecksService;

    @Mock
    AirmanRepository airmanRepo;

    private Airman airman;

    @BeforeEach
    public void makeAirman() {

        // an airman we'll use throughout
        airman = new Airman();
        airman.setFirstName("John");
        airman.setMiddleName("Hero");
        airman.setLastName("Public");
        airman.setEmail("john@test.com");
        airman.setTitle("Capt");
        airman.setAfsc("17D");
        airman.setPtDate(new Date(2020 - 1900, Calendar.OCTOBER, 1));
        airman.setEtsDate(new Date(2021 - 1900, Calendar.JUNE, 29));

        Mockito.when(uniqueChecksService.personEmailIsUnique(Mockito.any(Airman.class))).thenReturn(true);
    }

    @Test
    public void createAirmanTest() throws Exception {

        Mockito.when(airmanRepo.findAll())
                .thenReturn(Lists.newArrayList())  // reply with 0 on first call
                .thenReturn(Lists.newArrayList(airman)); // reply with the airman object

        Mockito.when(airmanRepo.existsById(Mockito.any(UUID.class))).thenReturn(false);
        int initialLength = Lists.newArrayList(airmanRepo.findAll()).size();
        airmanService.createAirman(airman);
        assertEquals(initialLength + 1, Lists.newArrayList(airmanRepo.findAll()).size());

        // test airman with same UUID throws
        airman.setEmail(null);
        Mockito.when(airmanRepo.existsById(Mockito.any(UUID.class))).thenReturn(true);
        assertThrows(ResourceAlreadyExistsException.class, () -> airmanService.createAirman(airman));

    }

    @Test
    public void createAirmanTestNullId() throws Exception {

        Mockito.when(airmanRepo.findAll())
                .thenReturn(Lists.newArrayList())  // reply with 0 on first call
                .thenReturn(Lists.newArrayList(airman)); // reply with the airman object

        Mockito.when(airmanRepo.existsById(Mockito.any(UUID.class))).thenReturn(false);
        int initialLength = Lists.newArrayList(airmanRepo.findAll()).size();

        airman.setId(null);
        airmanService.createAirman(airman);
        assertEquals(initialLength + 1, Lists.newArrayList(airmanRepo.findAll()).size());

    }

    @Test
    public void updateAirmanTest() throws Exception {

        Mockito.when(airmanRepo.save(Mockito.any(Airman.class))).then(returnsFirstArg());
        Mockito.when(airmanRepo.existsById(Mockito.any(UUID.class)))
                .thenReturn(false)
                .thenReturn(true);

        Airman savedAirman = airmanService.createAirman(airman);

        String newEmail = "joe2@test.com";
        savedAirman.setEmail(newEmail);
        Airman updatedAirman = airmanService.updateAirman(savedAirman.getId(), savedAirman);
        assertEquals(newEmail, updatedAirman.getEmail());

        // test updating another existing airman with an email that someone else has fails
        Airman newAirman = new Airman();
        Mockito.when(airmanRepo.findById(Mockito.any(UUID.class)))
                .thenReturn(Optional.of(newAirman));
        airman.setId(newAirman.getId());
        newAirman.setEmail("other@test.com"); // we'll mock one of this address already exists
        Mockito.when(uniqueChecksService.personEmailIsUnique(Mockito.any(Airman.class)))
                .thenReturn(false);
        assertThrows(InvalidRecordUpdateRequest.class, () -> airmanService.updateAirman(newAirman.getId(), airman));
    }

    @Test
    public void updateAirmanBadIdTest() throws Exception {
        Mockito.when(airmanRepo.save(Mockito.any(Airman.class))).then(returnsFirstArg());
        Mockito.when(airmanRepo.existsById(Mockito.any(UUID.class)))
                .thenReturn(false)
                .thenReturn(false);

        Airman savedAirman = airmanService.createAirman(airman);
        assertThrows(RecordNotFoundException.class, () -> airmanService.updateAirman(UUID.randomUUID(), savedAirman));
    }

    @Test
    public void updateAirmanDifferentIdTest() throws Exception {
        Mockito.when(airmanRepo.save(Mockito.any(Airman.class))).then(returnsFirstArg());
        Mockito.when(airmanRepo.existsById(Mockito.any(UUID.class)))
                .thenReturn(false)
                .thenReturn(false)
                .thenReturn(true);

        Airman airman2 = new Airman();
        airman2.setFirstName("Jim");
        airman2.setMiddleName("Hero");
        airman2.setLastName("Public");
        airman2.setEmail("jimbo@test.com");
        airman2.setTitle("SSGT");
        airman2.setAfsc("3D01");
        airman2.setPtDate(new Date(2020 - 1900, Calendar.OCTOBER, 1));
        airman2.setEtsDate(new Date(2021 - 1900, Calendar.JUNE, 29));

        Airman savedAirman = airmanService.createAirman(airman);
        Airman savedAirman2 = airmanService.createAirman(airman2);
        assertThrows(InvalidRecordUpdateRequest.class, () -> airmanService.updateAirman(savedAirman2.getId(), savedAirman));
    }

    @Test
    public void removeAirmanTest() throws Exception {
        Mockito.when(airmanRepo.save(Mockito.any(Airman.class))).then(returnsFirstArg());
        Mockito.when(airmanRepo.existsById(Mockito.any(UUID.class)))
                .thenReturn(false)
                .thenReturn(false)
                .thenReturn(true);
        doNothing().when(airmanRepo).deleteById(Mockito.any(UUID.class));
        Mockito.when(airmanRepo.findAll()).thenReturn(Lists.newArrayList());

        airmanService.createAirman(airman);
        assertThrows(RecordNotFoundException.class, () -> airmanService.removeAirman(airman.getId()));
        airmanService.removeAirman(airman.getId());
        assertEquals(0, Lists.newArrayList(airmanRepo.findAll()).size());
    }

    @Test
    public void getAirmanByIdTest() throws Exception {
        Mockito.when(airmanRepo.save(Mockito.any(Airman.class))).then(returnsFirstArg());
        Mockito.when(airmanRepo.existsById(Mockito.any(UUID.class)))
                .thenReturn(false)
                .thenReturn(true);
        Mockito.when(airmanRepo.findById(airman.getId())).thenReturn(Optional.of(airman));

        Airman savedAirman = airmanService.createAirman(airman);
        assertEquals(savedAirman.getId(), airmanService.getAirman(airman.getId()).getId());
    }

    @Test
    public void getAllAirmanTest() throws Exception {
        Mockito.when(airmanRepo.save(Mockito.any(Airman.class))).then(returnsFirstArg());
        Mockito.when(airmanRepo.findAll()).thenReturn(Lists.newArrayList(airman));

        Airman savedAirman = airmanService.createAirman(airman);
        assertEquals(1, Lists.newArrayList(airmanService.getAllAirman()).size());
    }

    @Test
    void bulkCreateAirmanTest() {
        Mockito.when(airmanRepo.existsById(Mockito.any(UUID.class))).thenReturn(false);
        Mockito.when(airmanRepo.save(Mockito.any(Airman.class))).then(returnsFirstArg());

        List<Airman> airmen = Lists.newArrayList(
                airman,
                new Airman(),
                new Airman(),
                new Airman(),
                new Airman()
        );

        List<Airman> createdAirmen = airmanService.bulkAddAirmen(airmen);
        assertThat(airmen).isEqualTo(createdAirmen);

        // test fails on adding someone with duplicate email
        Mockito.when(uniqueChecksService.personEmailIsUnique(Mockito.any(Airman.class))).thenReturn(false);
        List<Airman> moreAirmen = Lists.newArrayList(airman);
        assertThrows(ResourceAlreadyExistsException.class, () -> airmanService.bulkAddAirmen(moreAirmen));

    }

}

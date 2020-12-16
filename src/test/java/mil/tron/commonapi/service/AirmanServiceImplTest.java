package mil.tron.commonapi.service;

import mil.tron.commonapi.entity.Airman;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.AirmanRepository;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.transaction.Transactional;

import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class AirmanServiceImplTest {

    @Autowired
    AirmanService airmanService;

    @Autowired
    AirmanRepository airmanRepo;

    private Airman airman;

    @BeforeEach
    public void makeAirman() {
        airman = new Airman();
        airman.setFirstName("John");
        airman.setMiddleName("Hero");
        airman.setLastName("Public");
        airman.setEmail("john@test.com");
        airman.setTitle("Capt");
        airman.setAfsc("17D");
        airman.setPtDate(new Date(2020 - 1900, Calendar.OCTOBER, 1));
        airman.setEtsDate(new Date(2021 - 1900, Calendar.JUNE, 29));
    }

    @Transactional
    @Rollback
    @Test
    public void createAirmanTest() throws Exception {
        int initialLength = Lists.newArrayList(airmanRepo.findAll()).size();
        airmanService.createAirman(airman);
        assertEquals(initialLength + 1, Lists.newArrayList(airmanRepo.findAll()).size());

    }

    @Transactional
    @Rollback
    @Test
    public void updateAirmanTest() throws Exception {
        Airman savedAirman = airmanService.createAirman(airman);
        savedAirman.setEmail("joe2@test.com");
        Airman updatedAirman = airmanService.updateAirman(savedAirman.getId(), savedAirman);
        assertEquals("joe2@test.com", updatedAirman.getEmail());
    }

    @Transactional
    @Rollback
    @Test
    public void updateAirmanBadIdTest() throws Exception {
        Airman savedAirman = airmanService.createAirman(airman);
        assertThrows(RecordNotFoundException.class, () -> airmanService.updateAirman(UUID.randomUUID(), savedAirman));
    }

    @Transactional
    @Rollback
    @Test
    public void updateAirmanDifferentIdTest() throws Exception {
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

    @Transactional
    @Rollback
    @Test
    public void removeAirmanTest() throws Exception {
        airmanService.createAirman(airman);
        airmanService.removeAirman(airman.getId());
        assertEquals(0, Lists.newArrayList(airmanRepo.findAll()).size());
    }

    @Transactional
    @Rollback
    @Test
    public void getAirmanByIdTest() throws Exception {
        Airman savedAirman = airmanService.createAirman(airman);
        assertEquals(savedAirman.getId(), airmanService.getAirman(airman.getId()).getId());
    }

    @Transactional
    @Rollback
    @Test
    public void getAllAirmanTest() throws Exception {
        Airman savedAirman = airmanService.createAirman(airman);
        assertEquals(1, Lists.newArrayList(airmanService.getAllAirman()).size());
    }

}

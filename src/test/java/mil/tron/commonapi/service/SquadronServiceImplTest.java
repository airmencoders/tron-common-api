package mil.tron.commonapi.service;

import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.SquadronRepository;
import mil.tron.commonapi.squadron.Squadron;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.transaction.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class SquadronServiceImplTest {

    @Autowired
    SquadronService squadronService;

    @Autowired
    SquadronRepository squadronRepository;

    private Squadron squadron;

    @BeforeEach
    public void makeSquadron() {
        squadron = new Squadron();
        squadron.setName("TEST ORG");
        squadron.setMajorCommand("ACC");
        squadron.setBaseName("Travis AFB");
    }

    @Transactional
    @Rollback
    @Test
    public void createSquadronTest() throws Exception {
        int initialLength = Lists.newArrayList(squadronRepository.findAll()).size();
        squadronService.createSquadron(squadron);
        assertEquals(initialLength + 1, Lists.newArrayList(squadronRepository.findAll()).size());
    }

    @Transactional
    @Rollback
    @Test
    public void updateSquadronTest() throws Exception {
        Squadron savedSquadron = squadronService.createSquadron(squadron);
        savedSquadron.setBaseName("Grissom AFB");
        Squadron updatedSquadron = squadronService.updateSquadron(savedSquadron.getId(), savedSquadron);
        assertEquals("Grissom AFB", updatedSquadron.getBaseName());
    }

    @Transactional
    @Rollback
    @Test
    public void updateSquadronBadIdTest() throws Exception {
        Squadron savedSquadron = squadronService.createSquadron(squadron);
        assertThrows(RecordNotFoundException.class, () -> squadronService.updateSquadron(UUID.randomUUID(), savedSquadron));
    }

    @Transactional
    @Rollback
    @Test
    public void updateSquadronDifferentIdTest() throws Exception {
        Squadron sq2 = new Squadron();
        squadron.setName("TEST2 ORG");
        squadron.setMajorCommand("AETC");
        squadron.setBaseName("Hanscom AFB");

        Squadron savedSquadron = squadronService.createSquadron(squadron);
        Squadron savedSquadron2 = squadronService.createSquadron(sq2);

        assertThrows(InvalidRecordUpdateRequest.class, () -> squadronService.updateSquadron(savedSquadron2.getId(), savedSquadron));
    }

    @Transactional
    @Rollback
    @Test
    public void removeSquadronTest() throws Exception {
        squadronService.createSquadron(squadron);
        squadronService.removeSquadron(squadron.getId());
        assertEquals(0, Lists.newArrayList(squadronRepository.findAll()).size());
    }

    @Transactional
    @Rollback
    @Test
    public void getSquadronByIdTest() throws Exception {
        Squadron savedSquadron = squadronService.createSquadron(squadron);
        assertEquals(savedSquadron.getId(), squadronService.getSquadron(squadron.getId()).getId());
    }

    @Transactional
    @Rollback
    @Test
    public void getAllSquadronTest() throws Exception {
        Squadron savedSquadron = squadronService.createSquadron(squadron);
        assertEquals(1, Lists.newArrayList(squadronService.getAllSquadrons()).size());
    }
}


package mil.tron.commonapi.service;

import mil.tron.commonapi.entity.Airman;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.SquadronRepository;
import mil.tron.commonapi.entity.Squadron;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.transaction.Transactional;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class SquadronServiceImplTest {

    @Autowired
    SquadronService squadronService;

    @Autowired
    SquadronRepository squadronRepository;

    @Autowired
    AirmanService airmanService;

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

    @Transactional
    @Rollback
    @Test
    public void testChangeSquadronAttributes() {
        Squadron savedSquadron = squadronService.createSquadron(squadron);
        Airman savedAirman = airmanService.createAirman(new Airman());
        Map<String, String> attribs = new HashMap<>();

        // test change to bogus squadron fails
        assertThrows(RecordNotFoundException.class,
                () -> squadronService.modifySquadronAttributes(new Squadron().getId(), attribs));

        // test can change leader
        attribs.put("leader", savedAirman.getId().toString());
        assertEquals(savedAirman.getId().toString(),
                squadronService.modifySquadronAttributes(savedSquadron.getId(), attribs)
                        .getLeader()
                        .getId()
                        .toString());

        // test can change director
        attribs.put("operationsDirector", savedAirman.getId().toString());
        assertEquals(savedAirman.getId().toString(),
                squadronService.modifySquadronAttributes(savedSquadron.getId(), attribs)
                        .getOperationsDirector()
                        .getId()
                        .toString());

        // test can change chief
        attribs.put("chief", savedAirman.getId().toString());
        assertEquals(savedAirman.getId().toString(),
                squadronService.modifySquadronAttributes(savedSquadron.getId(), attribs)
                        .getChief()
                        .getId()
                        .toString());

        // test can change base name
        attribs.put("baseName", "Test");
        assertEquals("Test",
                squadronService.modifySquadronAttributes(savedSquadron.getId(), attribs)
                        .getBaseName());

        // test can change major command
        attribs.put("majorCommand", "Test2");
        assertEquals("Test2",
                squadronService.modifySquadronAttributes(savedSquadron.getId(), attribs)
                        .getMajorCommand());

    }

    @Transactional
    @Rollback
    @Test
    public void testAddRemoveMembers() {
        Squadron savedSquadron = squadronService.createSquadron(squadron);
        Airman savedAirman = airmanService.createAirman(new Airman());

        assertEquals(1, squadronService.addSquadronMember(savedSquadron.getId(),
                Lists.newArrayList(savedAirman.getId())).getMembers().size());

        // croaks on adding an invalid airman UUID
        assertThrows(InvalidRecordUpdateRequest.class,
                () -> squadronService.addSquadronMember(savedSquadron.getId(), Lists.newArrayList(new Airman().getId())));

        assertEquals(0, squadronService.removeSquadronMember(savedSquadron.getId(),
                Lists.newArrayList(savedAirman.getId())).getMembers().size());

        // croaks on removing an invalid airman UUID
        assertThrows(InvalidRecordUpdateRequest.class,
                () -> squadronService.removeSquadronMember(savedSquadron.getId(), Lists.newArrayList(new Airman().getId())));
    }
}


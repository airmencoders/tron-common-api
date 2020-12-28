package mil.tron.commonapi.service;

import mil.tron.commonapi.entity.Airman;
import mil.tron.commonapi.entity.Squadron;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.AirmanRepository;
import mil.tron.commonapi.repository.SquadronRepository;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Mockito.doNothing;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class SquadronServiceImplTest {

    @InjectMocks
    SquadronServiceImpl squadronService;

    @Mock
    SquadronRepository squadronRepository;

    @Mock
    AirmanServiceImpl airmanService;

    @Mock
    OrganizationServiceImpl orgService;

    @Mock
    AirmanRepository airmanRepo;

    private Squadron squadron;

    @BeforeEach
    public void makeSquadron() {
        squadron = new Squadron();
        squadron.setName("TEST ORG");
        squadron.setMajorCommand("ACC");
        squadron.setBaseName("Travis AFB");
    }

    @Test
    public void createSquadronTest() throws Exception {
        Mockito.when(squadronRepository.findAll())
                .thenReturn(Lists.newArrayList())
                .thenReturn(Lists.newArrayList(squadron));

        int initialLength = Lists.newArrayList(squadronRepository.findAll()).size();
        squadronService.createSquadron(squadron);
        assertEquals(initialLength + 1, Lists.newArrayList(squadronRepository.findAll()).size());
    }

    @Test
    public void createSquadronTestNullId() throws Exception {
        Mockito.when(squadronRepository.findAll())
                .thenReturn(Lists.newArrayList())
                .thenReturn(Lists.newArrayList(squadron));

        int initialLength = Lists.newArrayList(squadronRepository.findAll()).size();
        squadron.setId(null);
        squadronService.createSquadron(squadron);
        assertEquals(initialLength + 1, Lists.newArrayList(squadronRepository.findAll()).size());
    }

    @Test
    public void updateSquadronTest() throws Exception {
        Mockito.when(squadronRepository.save(Mockito.any(Squadron.class))).then(returnsFirstArg());
        Mockito.when(squadronRepository.existsById(Mockito.any(UUID.class)))
                .thenReturn(false)
                .thenReturn(true);

        Squadron savedSquadron = squadronService.createSquadron(squadron);
        savedSquadron.setBaseName("Grissom AFB");
        Squadron updatedSquadron = squadronService.updateSquadron(savedSquadron.getId(), savedSquadron);
        assertEquals("Grissom AFB", updatedSquadron.getBaseName());
    }

    @Test
    public void updateSquadronBadIdTest() throws Exception {
        Mockito.when(squadronRepository.save(Mockito.any(Squadron.class))).then(returnsFirstArg());
        Mockito.when(squadronRepository.existsById(Mockito.any(UUID.class)))
                .thenReturn(false)
                .thenReturn(false);

        Squadron savedSquadron = squadronService.createSquadron(squadron);
        assertThrows(RecordNotFoundException.class, () -> squadronService.updateSquadron(UUID.randomUUID(), savedSquadron));
    }

    @Test
    public void updateSquadronDifferentIdTest() throws Exception {
        Mockito.when(squadronRepository.save(Mockito.any(Squadron.class))).then(returnsFirstArg());
        Mockito.when(squadronRepository.existsById(Mockito.any(UUID.class)))
                .thenReturn(false)
                .thenReturn(false)
                .thenReturn(true);

        Squadron sq2 = new Squadron();
        sq2.setName("TEST2 ORG");
        sq2.setMajorCommand("AETC");
        sq2.setBaseName("Hanscom AFB");

        Squadron savedSquadron = squadronService.createSquadron(squadron);
        Squadron savedSquadron2 = squadronService.createSquadron(sq2);

        assertThrows(InvalidRecordUpdateRequest.class, () -> squadronService.updateSquadron(savedSquadron2.getId(), savedSquadron));
    }

    @Test
    public void removeSquadronTest() throws Exception {
        Mockito.when(squadronRepository.save(Mockito.any(Squadron.class))).then(returnsFirstArg());
        Mockito.when(squadronRepository.existsById(Mockito.any(UUID.class)))
                .thenReturn(false)
                .thenReturn(true)
                .thenReturn(false);
        doNothing().when(squadronRepository).deleteById(Mockito.any(UUID.class));
        Mockito.when(squadronRepository.findAll()).thenReturn(Lists.newArrayList());

        squadronService.createSquadron(squadron);
        squadronService.removeSquadron(squadron.getId());
        assertEquals(0, Lists.newArrayList(squadronRepository.findAll()).size());
        assertThrows(RecordNotFoundException.class, () -> squadronService.removeSquadron(squadron.getId()));
    }

    @Test
    public void getSquadronByIdTest() throws Exception {
        Mockito.when(squadronRepository.save(Mockito.any(Squadron.class))).then(returnsFirstArg());
        Mockito.when(squadronRepository.existsById(Mockito.any(UUID.class)))
                .thenReturn(false)
                .thenReturn(true);
        Mockito.when(squadronRepository.findById(squadron.getId())).thenReturn(Optional.of(squadron));

        Squadron savedSquadron = squadronService.createSquadron(squadron);
        assertEquals(savedSquadron.getId(), squadronService.getSquadron(squadron.getId()).getId());
    }

    @Test
    public void getAllSquadronTest() throws Exception {
        Mockito.when(squadronRepository.save(Mockito.any(Squadron.class))).then(returnsFirstArg());
        Mockito.when(squadronRepository.findAll()).thenReturn(Lists.newArrayList(squadron));

        Squadron savedSquadron = squadronService.createSquadron(squadron);
        assertEquals(1, Lists.newArrayList(squadronService.getAllSquadrons()).size());
    }

    @Test
    public void testChangeSquadronAttributes() {
        Mockito.when(squadronRepository.save(Mockito.any(Squadron.class))).then(returnsFirstArg());
        Mockito.when(squadronRepository.existsById(Mockito.any(UUID.class)))
                .thenReturn(false)
                .thenReturn(true)
                .thenThrow(new RecordNotFoundException("Not found"))
                .thenReturn(true);
        Mockito.when(orgService.modifyAttributes(Mockito.any(UUID.class), Mockito.anyMap()))
                .thenReturn(null)
                .thenThrow(new RecordNotFoundException("Not found"))
                .thenReturn(squadron);

        Airman airman = new Airman();
        Mockito.when(airmanService.createAirman(Mockito.any(Airman.class))).then(returnsFirstArg());
        Mockito.when(airmanRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(airman));

        Squadron savedSquadron = squadronService.createSquadron(squadron);
        Airman savedAirman = airmanService.createAirman(airman);
        Map<String, String> attribs = new HashMap<>();

        // test Parent class returns null
        assertThrows(InvalidRecordUpdateRequest.class,
                () -> squadronService.modifySquadronAttributes(new Squadron().getId(), attribs));

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

    @Test
    public void testAddRemoveMembers() {
        Mockito.when(squadronRepository.save(Mockito.any(Squadron.class))).then(returnsFirstArg());
        Mockito.when(squadronRepository.existsById(Mockito.any(UUID.class)))
                .thenReturn(false)
                .thenReturn(true);

        Mockito.when(orgService.addOrganizationMember(Mockito.any(UUID.class), Mockito.anyList()))
                .thenReturn(squadron)
                .thenThrow(new InvalidRecordUpdateRequest("Invalid UUID"))
                .thenReturn(null);

        Mockito.when(orgService.removeOrganizationMember(Mockito.any(UUID.class), Mockito.anyList()))
                .thenReturn(squadron)
                .thenThrow(new InvalidRecordUpdateRequest("Invalid UUID"))
                .thenReturn(null);

        Mockito.when(airmanService.createAirman(Mockito.any(Airman.class))).then(returnsFirstArg());

        Squadron savedSquadron = squadronService.createSquadron(squadron);
        Airman savedAirman = airmanService.createAirman(new Airman());

        squadron.addMember(savedAirman);  // mock the member that got added
        assertEquals(1, squadronService
                .addSquadronMember(savedSquadron.getId(), Lists.newArrayList(savedAirman.getId()))
                .getMembers()
                .size());

        // croaks on adding an invalid airman UUID
        assertThrows(InvalidRecordUpdateRequest.class,
                () -> squadronService.addSquadronMember(savedSquadron.getId(), Lists.newArrayList(new Airman().getId())));

        // croaks when Parent class returns null
        assertThrows(InvalidRecordUpdateRequest.class,
                () -> squadronService.addSquadronMember(savedSquadron.getId(), Lists.newArrayList(new Airman().getId())));

        squadron.removeMember(savedAirman);  // mock removal

        assertEquals(0, squadronService
                .removeSquadronMember(savedSquadron.getId(), Lists.newArrayList(savedAirman.getId()))
                .getMembers()
                .size());

        // croaks on removing an invalid airman UUID
        assertThrows(InvalidRecordUpdateRequest.class,
                () -> squadronService.removeSquadronMember(savedSquadron.getId(), Lists.newArrayList(new Airman().getId())));

        // croaks when Parent class returns null
        assertThrows(InvalidRecordUpdateRequest.class,
                () -> squadronService.removeSquadronMember(savedSquadron.getId(), Lists.newArrayList(new Airman().getId())));

    }
}


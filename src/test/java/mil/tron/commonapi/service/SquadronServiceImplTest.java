package mil.tron.commonapi.service;

import mil.tron.commonapi.dto.SquadronDto;
import mil.tron.commonapi.entity.Airman;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.Squadron;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.AirmanRepository;
import mil.tron.commonapi.repository.SquadronRepository;
import mil.tron.commonapi.service.utility.OrganizationUniqueChecksServiceImpl;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.modelmapper.ModelMapper;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

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
    PersonService personService;

    @Mock
    AirmanRepository airmanRepo;

    @Mock
    OrganizationUniqueChecksServiceImpl uniqueService;

    private Squadron squadron;
    private SquadronDto squadronDto;

    @BeforeEach
    public void makeSquadron() {
        squadron = new Squadron();
        squadron.setName("TEST ORG");
        squadron.setMajorCommand("ACC");
        squadron.setBaseName("Travis AFB");

        squadronDto = new SquadronDto();
        squadronDto.setId(squadron.getId());
        squadronDto.setName(squadron.getName());
        squadronDto.setMajorCommand(squadron.getMajorCommand());
        squadronDto.setBaseName(squadron.getBaseName());

        Mockito.when(uniqueService.orgNameIsUnique(Mockito.any(Squadron.class))).thenReturn(true);
    }

    @Test
    public void createSquadronTest() throws Exception {
        Mockito.when(squadronRepository.findAll())
                .thenReturn(Lists.newArrayList())
                .thenReturn(Lists.newArrayList(squadron));

        int initialLength = Lists.newArrayList(squadronRepository.findAll()).size();
        squadronService.createSquadron(squadronDto);
        assertEquals(initialLength + 1, Lists.newArrayList(squadronRepository.findAll()).size());

        // test squadron with same UUID throws
        squadronDto.setName(null);
        Mockito.when(squadronRepository.existsById(Mockito.any(UUID.class))).thenReturn(true);
        assertThrows(ResourceAlreadyExistsException.class, () -> squadronService.createSquadron(squadronDto));
    }

    @Test
    public void createSquadronTestNullId() throws Exception {
        Mockito.when(squadronRepository.findAll())
                .thenReturn(Lists.newArrayList())
                .thenReturn(Lists.newArrayList(squadron));

        int initialLength = Lists.newArrayList(squadronRepository.findAll()).size();
        squadronDto.setId(null);
        squadronService.createSquadron(squadronDto);
        assertEquals(initialLength + 1, Lists.newArrayList(squadronRepository.findAll()).size());
    }

    @Test
    public void updateSquadronTest() throws Exception {
        Mockito.when(squadronRepository.save(Mockito.any(Squadron.class))).then(returnsFirstArg());
        Mockito.when(squadronRepository.existsById(Mockito.any(UUID.class)))
                .thenReturn(false)
                .thenReturn(true);

        Mockito.when(uniqueService.orgNameIsUnique(Mockito.any(Squadron.class)))
                .thenReturn(true);

        SquadronDto savedSquadron = squadronService.createSquadron(squadronDto);
        savedSquadron.setBaseName("Grissom AFB");
        SquadronDto updatedSquadron = squadronService.updateSquadron(savedSquadron.getId(), savedSquadron);
        assertEquals("Grissom AFB", updatedSquadron.getBaseName());

        // test updating squadron with different UUID vs UUID in the sqdn dto fails
        SquadronDto newSquadron = new SquadronDto();
        assertThrows(InvalidRecordUpdateRequest.class, () ->
                squadronService.updateSquadron(newSquadron.getId(), squadronDto));

        // test updating a squadron with a name that already exists fails
        newSquadron.setName("test");  // we'll mock this name already exists
        Mockito.when(uniqueService.orgNameIsUnique(Mockito.any(Squadron.class))).thenReturn(false);
        assertThrows(InvalidRecordUpdateRequest.class, () ->
                squadronService.updateSquadron(squadronDto.getId(), squadronDto));
    }

    @Test
    public void updateSquadronBadIdTest() throws Exception {
        Mockito.when(squadronRepository.save(Mockito.any(Squadron.class))).then(returnsFirstArg());
        Mockito.when(squadronRepository.existsById(Mockito.any(UUID.class)))
                .thenReturn(false)
                .thenReturn(false);

        SquadronDto savedSquadron = squadronService.createSquadron(squadronDto);
        assertThrows(RecordNotFoundException.class, () -> squadronService.updateSquadron(UUID.randomUUID(), squadronDto));
    }

    @Test
    public void updateSquadronDifferentIdTest() throws Exception {
        Mockito.when(squadronRepository.save(Mockito.any(Squadron.class))).then(returnsFirstArg());
        Mockito.when(squadronRepository.existsById(Mockito.any(UUID.class)))
                .thenReturn(false)
                .thenReturn(false)
                .thenReturn(true);


        SquadronDto sq2 = new SquadronDto();
        sq2.setName("TEST2 ORG");
        sq2.setMajorCommand("AETC");
        sq2.setBaseName("Hanscom AFB");

        SquadronDto savedSquadron = squadronService.createSquadron(squadronDto);
        SquadronDto savedSquadron2 = squadronService.createSquadron(sq2);

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

        squadronService.createSquadron(squadronDto);
        squadronService.removeSquadron(squadronDto.getId());
        assertEquals(0, Lists.newArrayList(squadronRepository.findAll()).size());
        assertThrows(RecordNotFoundException.class, () -> squadronService.removeSquadron(squadronDto.getId()));
    }

    @Test
    public void getSquadronByIdTest() throws Exception {
        Mockito.when(squadronRepository.save(Mockito.any(Squadron.class))).then(returnsFirstArg());
        Mockito.when(squadronRepository.existsById(Mockito.any(UUID.class)))
                .thenReturn(false)
                .thenReturn(true);
        Mockito.when(squadronRepository.findById(squadron.getId())).thenReturn(Optional.of(squadron));

        SquadronDto savedSquadron = squadronService.createSquadron(squadronDto);
        assertEquals(savedSquadron.getId(), squadronService.getSquadron(squadronDto.getId()).getId());
    }

    @Test
    public void getAllSquadronTest() throws Exception {
        Mockito.when(squadronRepository.save(Mockito.any(Squadron.class))).then(returnsFirstArg());
        Mockito.when(squadronRepository.findAll()).thenReturn(Lists.newArrayList(squadron));

        SquadronDto savedSquadron = squadronService.createSquadron(squadronDto);
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

        Mockito.when(orgService.modify(Mockito.any(UUID.class), Mockito.anyMap()))
                .thenReturn(null)
                .thenThrow(new RecordNotFoundException("Not found"))
                .thenReturn(squadron);

        Airman airman = new Airman();
        Mockito.when(airmanService.createAirman(Mockito.any(Airman.class))).then(returnsFirstArg());
        Mockito.when(airmanRepo.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(airman));

        SquadronDto savedSquadronDto = squadronService.createSquadron(squadronDto);
        Mockito.when(orgService.modify(Mockito.any(UUID.class), Mockito.anyMap()))
                .thenReturn(null)
                .thenThrow(new RecordNotFoundException("Not found"))
                .thenReturn(squadron);


        Airman savedAirman = airmanService.createAirman(airman);
        Map<String, String> attribs = new HashMap<>();
        Mockito.when(personService.getPerson(Mockito.any(UUID.class))).thenReturn(savedAirman);

        // test Parent class returns null
        assertThrows(InvalidRecordUpdateRequest.class,
                () -> squadronService.modifySquadronAttributes(new Squadron().getId(), attribs));

        // test change to bogus squadron fails
        assertThrows(RecordNotFoundException.class,
                () -> squadronService.modifySquadronAttributes(new Squadron().getId(), attribs));

        // test can change director
        attribs.put("operationsDirector", savedAirman.getId().toString());
        assertEquals(savedAirman.getId(),
                squadronService.modifySquadronAttributes(savedSquadronDto.getId(), attribs)
                        .getOperationsDirector());

        // test can change chief
        attribs.put("chief", savedAirman.getId().toString());
        assertEquals(savedAirman.getId(),
                squadronService.modifySquadronAttributes(savedSquadronDto.getId(), attribs)
                        .getChief());

        // test can change base name
        attribs.put("baseName", "Test");
        assertEquals("Test",
                squadronService.modifySquadronAttributes(savedSquadronDto.getId(), attribs)
                        .getBaseName());

        // test can change major command
        attribs.put("majorCommand", "Test2");
        assertEquals("Test2",
                squadronService.modifySquadronAttributes(savedSquadronDto.getId(), attribs)
                        .getMajorCommand());

        // test trying to change the id fails

    }

    @Test
    public void testAddRemoveMembers() {
        Mockito.when(squadronRepository.save(Mockito.any(Squadron.class))).then(returnsFirstArg());
        Mockito.when(squadronRepository.existsById(Mockito.any(UUID.class)))
                .thenReturn(false)
                .thenReturn(true);

        Mockito.when(orgService.addMember(Mockito.any(UUID.class), Mockito.anyList()))
                .thenReturn(squadron)
                .thenThrow(new InvalidRecordUpdateRequest("Invalid UUID"))
                .thenReturn(null);

        Mockito.when(orgService.removeMember(Mockito.any(UUID.class), Mockito.anyList()))
                .thenReturn(squadron)
                .thenThrow(new InvalidRecordUpdateRequest("Invalid UUID"))
                .thenReturn(null);

        Mockito.when(airmanService.createAirman(Mockito.any(Airman.class))).then(returnsFirstArg());

        SquadronDto savedSquadron = squadronService.createSquadron(squadronDto);
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

    @Test
    void testBulkAddSquadrons() {
        Mockito.when(squadronRepository.existsById(Mockito.any(UUID.class))).thenReturn(false);
        Mockito.when(squadronRepository.save(Mockito.any(Squadron.class))).then(returnsFirstArg());
        List<SquadronDto> newSquads = Lists.newArrayList(
                squadronService.convertToDto(squadron),
                squadronService.convertToDto(new Squadron()),
                squadronService.convertToDto(new Squadron())
        );

        List<SquadronDto> addedSquads = squadronService.bulkAddSquadrons(newSquads);
        assertEquals(newSquads, addedSquads);

        // test fails on adding a squadron with a duplicate name
        Mockito.when(uniqueService.orgNameIsUnique(Mockito.any(Squadron.class))).thenReturn(false);
        List<SquadronDto> moreSquads = Lists.newArrayList(squadronService.convertToDto(squadron));
        assertThrows(ResourceAlreadyExistsException.class, () -> squadronService.bulkAddSquadrons(moreSquads));
    }

    @Test
    void testMapToDto() {
        Person chief = new Person();
        Squadron parent = new Squadron();
        Squadron suborg = new Squadron();
        Squadron org = new Squadron();
        org.setName("test");
        org.setParentOrganization(parent);
        org.addSubordinateOrganization(suborg);
        org.setChief(chief);

        SquadronDto dto = new ModelMapper().map(org, SquadronDto.class);
        assertEquals(dto, squadronService.convertToDto(org));
    }

    @Test
    void testDtoToSquadron() {
        Person chief = new Person();
        Squadron parent = new Squadron();
        Squadron suborg = new Squadron();
        Squadron org = new Squadron();
        org.setParentOrganization(parent);
        org.addSubordinateOrganization(suborg);
        org.setChief(chief);

        SquadronDto dto = new SquadronDto();
        dto.setId(org.getId());
        dto.setParentOrganizationUUID(parent.getId());
        dto.setName(org.getName());
        dto.setChief(org.getChief());
        dto.setSubordinateOrganizations(org.getSubordinateOrganizations());

        Mockito.when(orgService.findOrganization(dto.getId())).thenReturn(org);
        Mockito.when(personService.getPerson(chief.getId())).thenReturn(chief);

        assertEquals(org, squadronService.convertToEntity(dto));
    }
}


package mil.tron.commonapi.service.puckboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import mil.tron.commonapi.dto.OrganizationDto;
import mil.tron.commonapi.entity.Airman;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.AirmanRepository;
import mil.tron.commonapi.repository.OrganizationRepository;
import mil.tron.commonapi.service.AirmanServiceImpl;
import mil.tron.commonapi.service.OrganizationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.AdditionalAnswers.returnsSecondArg;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class PuckboardExtractorServiceImplTest {

    @Mock
    private OrganizationRepository orgRepo;

    @Mock
    private OrganizationServiceImpl orgService;

    @Mock
    private AirmanRepository airmanRepository;

    @Mock
    private AirmanServiceImpl airmanService;

    @InjectMocks
    private PuckboardExtractorServiceImpl puckboardExtractorService;

    private final ObjectMapper mapper = new ObjectMapper();

    JsonNode branchNodes, orgNodes, peopleNodes;
    int unitCount, peopleCount;

    @BeforeEach
    void setup() throws IOException {
        branchNodes = mapper.readTree(
                Resources.toString(
                        Resources.getResource("puckboard/mock-branches.json"), StandardCharsets.UTF_8));

        orgNodes = mapper.readTree(
                Resources.toString(
                        Resources.getResource("puckboard/mock-organizations.json"), StandardCharsets.UTF_8));

        peopleNodes = mapper.readTree(
                Resources.toString(
                        Resources.getResource("puckboard/mock-personnel.json"), StandardCharsets.UTF_8));

        unitCount = 0;
        peopleCount = peopleNodes.size();

        // determine number of squadrons we have head of time to compare with
        List<JsonNode> units = ImmutableList.copyOf(orgNodes.elements());
        unitCount = units.size();

    }

    @Test
    void testPersistOrgsAndMembersCreate() {

        // set up GO path - where every Org gets added
        Mockito.when(airmanRepository.existsById(Mockito.any(UUID.class))).thenReturn(false);
        Mockito.when(airmanService.createAirman(Mockito.any(Airman.class))).then(returnsFirstArg());
        Mockito.when(airmanService.updateAirman(Mockito.any(UUID.class), Mockito.any(Airman.class))).then(returnsSecondArg());
        Mockito.when(orgRepo.existsById(Mockito.any(UUID.class))).thenReturn(false);
        Mockito.when(orgService.createOrganization(Mockito.any(OrganizationDto.class))).then(returnsFirstArg());
        Mockito.when(orgService.updateOrganization(Mockito.any(UUID.class), Mockito.any(OrganizationDto.class))).then(returnsSecondArg());

        Map<String, Map<UUID, String>> result = puckboardExtractorService.persistOrgsAndMembers(orgNodes, peopleNodes, branchNodes);

        assertEquals(unitCount, result.get("orgs").size());
        assertEquals(peopleCount, result.get("people").size());

    }

    @Test
    void testPersistOrgsAndMembersUpdate() {

        // set up GO path - where every Org gets UPDATED
        Mockito.when(airmanRepository.existsById(Mockito.any(UUID.class))).thenReturn(true);
        Mockito.when(airmanService.createAirman(Mockito.any(Airman.class))).then(returnsFirstArg());
        Mockito.when(airmanService.updateAirman(Mockito.any(UUID.class), Mockito.any(Airman.class))).then(returnsSecondArg());
        Mockito.when(orgRepo.existsById(Mockito.any(UUID.class))).thenReturn(true);
        Mockito.when(orgService.createOrganization(Mockito.any(OrganizationDto.class))).then(returnsFirstArg());
        Mockito.when(orgService.updateOrganization(Mockito.any(UUID.class), Mockito.any(OrganizationDto.class))).then(returnsSecondArg());

        Map<String, Map<UUID, String>> result = puckboardExtractorService.persistOrgsAndMembers(orgNodes, peopleNodes, branchNodes);

        assertEquals(unitCount, result.get("orgs").size());
        assertEquals(peopleCount, result.get("people").size());
    }

    @Test
    void testPersistOrgsAndMembersExceptions() {

        // set up GO path - where every Org gets UPDATED
        Mockito.when(airmanRepository.existsById(Mockito.any(UUID.class))).thenReturn(false);
        Mockito.when(airmanService.createAirman(Mockito.any(Airman.class)))
                .thenThrow(new RecordNotFoundException("Not Found"))  // throw exception on first item
                .then(returnsFirstArg());

        Mockito.when(airmanService.updateAirman(Mockito.any(UUID.class), Mockito.any(Airman.class))).then(returnsSecondArg());
        Mockito.when(orgRepo.existsById(Mockito.any(UUID.class))).thenReturn(true);
        Mockito.when(orgService.createOrganization(Mockito.any(OrganizationDto.class))).then(returnsFirstArg());
        Mockito.when(orgService.updateOrganization(Mockito.any(UUID.class), Mockito.any(OrganizationDto.class))).then(returnsSecondArg());

        Map<String, Map<UUID, String>> result = puckboardExtractorService.persistOrgsAndMembers(orgNodes, peopleNodes, branchNodes);

        assertEquals(unitCount, result.get("orgs").size());

        // # people "created" should be one less since we threw exception on first item attempted to be created
        assertEquals(peopleCount-1, (int) result
                                                .get("people")
                                                .values()
                                                .stream()
                                                .filter(val -> val.contains("Created")).count());
    }
}

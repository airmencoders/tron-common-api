package mil.tron.commonapi.service.puckboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import mil.tron.commonapi.dto.OrganizationDto;
import mil.tron.commonapi.entity.Airman;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.orgtypes.Unit;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.AirmanRepository;
import mil.tron.commonapi.repository.OrganizationRepository;
import mil.tron.commonapi.service.AirmanService;
import mil.tron.commonapi.service.OrganizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionSystemException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PuckboardExtractorServiceImpl implements PuckboardExtractorService {

    @Autowired
    private OrganizationService orgService;

    @Autowired
    private OrganizationRepository orgRepo;

    @Autowired
    private AirmanRepository airmanRepo;

    @Autowired
    private AirmanService airmanService;

    private static final String ORG_ID_FIELD = "organizationId";
    private static final String ORG_TYPE_FIELD = "branchId";
    private static final String ORG_NAME_FIELD = "organizationName";
    private static final String ORG_STATUS_FIELD = "organizationStatus";

    private static final String PERSON_ID_FIELD = "id";
    private static final String PERSON_FIRST_NAME_FIELD = "firstName";
    private static final String PERSON_LAST_NAME_FIELD = "lastName";
    private static final String PERSON_EMAIL_FIELD = "email";
    private static final String PERSON_DODID_FIELD = "dodId";
    private static final String PERSON_PHONE_FIELD = "contactNumber";
    private static final String PERSON_RANK_FIELD = "rankId";
    private static final String PERSON_RANK_ABBR_FIELD = "rankAbbr";

    private static final int PB_MARINE_BRANCH = 2;

    /**
     * Convert the JSON as given by the controller (as a JsonNode)
     * so that we can parse through it and build a rankId -> rankAbbr
     * lookup table since Puckboard saves 'rankId' for a person's rank and
     * Common API uses a set of pre-defined rank strings.
     * Puckboard's rank entity is an array of all the services as a object, like this:
     *  [
     *      {
     *          "id": 4,
     *          "name": "Air Force",
     *          "enlistedRanks": [
     *              {
     *                  "rankName": "Airman Basic",
     *                  "payGrade": "E-1",
     *                  "rankAbbr": "AB",
     *                  "rankStatus": "Enlisted",
     *                  "rankId": 1
     *              },
     *              ...
     *           ],
     *           "warrantOfficerRanks": []
     *           "officerRanks": []
     *           ...
     *      },
     *      ...
     * ]
     * @param branchInfoJson The JsonNode given by the controller from the Puckboard API branch info dump
     */
    private Map<String, String> processBranchInfo(JsonNode branchInfoJson) {
        Map<String, String> allRanks = new HashMap<>();
        for (int i = 0; i < branchInfoJson.size(); i++) {
            JsonNode node = branchInfoJson.get(i);
            for (String rankSetName : Lists.newArrayList("enlistedRanks", "officerRanks", "warrantOfficerRanks")) {
                JsonNode rankSet = node.get(rankSetName);
                for (int j = 0; j < rankSet.size(); j++) {
                    JsonNode rankNode = rankSet.get(j);
                    allRanks.put(rankNode.get(PERSON_RANK_FIELD).asText(), rankNode.get(PERSON_RANK_ABBR_FIELD).textValue());
                }
            }
        }

        return allRanks;
    }

    // helper function to convert PB's branchId to service name
    private Branch resolveServiceName(JsonNode name) {
        // infer the service from name
        if (name != null && name.intValue() == PB_MARINE_BRANCH) return Branch.USMC;
        else return Branch.USAF;
    }

    // helper function to infer unit type by its name
    private Unit resolveUnitType(JsonNode node) {
        if (node != null) {
            if (node.textValue().toLowerCase().contains("squadron")) return Unit.SQUADRON;
            else if (node.textValue().toLowerCase().contains("wing")) return Unit.WING;
        }

        return Unit.OTHER_USAF;
    }

    /**
     * Processes Squadron information from a JsonNode structure that contains the organization dump from puckboard.
     * Resultant units are then queried for existence in Common API by their UUID:
     *  If they don't exist, then they are added via the OrganizationService.  Only UUID and name are used for creation.
     *  If they do exist by UUID, the entity is modified/updated by updating the unit's name.
     *
     * Each unit is placed into a return Map with its UUID and if it was created/updated/or the error it caused.
     * @param orgInfo JsonNode dump of Puckboard's organizations
     * @return Map of units added/updated to common API by UUID and its result (created/updated/error details)
     */
    private Map<UUID, String> processOrgInformation(JsonNode orgInfo) {

        Map<UUID, String> unitIdStatus = new HashMap<>();

        // go thru each unit and add/update to Common
        for (JsonNode node : orgInfo) {
            UUID id = UUID.fromString(node.get(ORG_ID_FIELD).textValue());
            String name = node.get(ORG_NAME_FIELD).textValue();
            if (!orgRepo.existsById(id)) {
                // make new unit
                OrganizationDto s = new OrganizationDto();
                s.setId(id);
                s.setName(name);
                s.setBranchType(resolveServiceName(node.get(ORG_TYPE_FIELD)));
                s.setOrgType(resolveUnitType(node.get(ORG_NAME_FIELD)));
                orgService.createOrganization(s);
                unitIdStatus.put(id, "Created - " + name);
            }
            else {
                // unit exists, just update the unit name
                orgService.modifyAttributes(id,
                        new ImmutableMap
                                .Builder<String, String>()
                                .put("name", name)
                                .build());

                unitIdStatus.put(id, "Updated - " + name);
            }
        }

        return unitIdStatus;
    }

    /**
     * Processes personnel information from puckboard personnel dump.
     * Each entity from puckboard is tested to see if it exists in Common API by its UUID.
     * If it doesn't exist, then it is added as an Airman.
     * If it does exist, then it existing record is updated/overwritten with the data from the puckboard dump.
     * Finally, the organizations the entity belonged to in puckboard are updated in Common API with the personnel's
     * UUID.  Conversely, that UUID is removed from the said organization if it has an "active" status of "false" in the
     * puckboard data.
     * @param peopleInfo JsonNode containing the personnel dump from puckboard
     * @param branchLookup Lookup table of puckboard's rank structure built from {@link #processBranchInfo(JsonNode)}
     * @return Map of personnel data added/updated to Common API by the UUID and if the entity was added/updated or had an error
     */
    private Map<UUID, String> processPersonnelInfo(JsonNode peopleInfo, Map<String, String> branchLookup) {

        Map<UUID, String> personIdStatus = new HashMap<>();

        // go thru each person and add/update to Common
        for (int i = 0; i < peopleInfo.size(); i++) {
            JsonNode node = peopleInfo.get(i);

            UUID id = UUID.fromString(node.get(PERSON_ID_FIELD).textValue());
            Airman airman = new Airman();
            airman.setDodid(node.get(PERSON_DODID_FIELD).isNull() ? null : node.get(PERSON_DODID_FIELD).asText());
            airman.setDutyPhone(node.get(PERSON_PHONE_FIELD).textValue());
            airman.setTitle(branchLookup.get(node.get(PERSON_RANK_FIELD).asText()));
            airman.setId(id);
            airman.setFirstName(node.get(PERSON_FIRST_NAME_FIELD).textValue());
            airman.setLastName(node.get(PERSON_LAST_NAME_FIELD).textValue());
            airman.setEmail(node.get(PERSON_EMAIL_FIELD).textValue());

            try {
                if (!airmanRepo.existsById(id)) {

                    // create new airman
                    airmanService.createAirman(airman);
                    personIdStatus.put(id, "Created - " + airman.getFullName());
                } else {
                    airmanService.updateAirman(id, airman);
                    personIdStatus.put(id, "Updated - " + airman.getFullName());
                }

                // now go thru each puckboard org person was in - either add or remove depending on active status
                List<String> personOrgIds = ImmutableList.copyOf(node.get(ORG_STATUS_FIELD).fieldNames());
                for (String org : personOrgIds) {
                    JsonNode orgNode = node.get(ORG_STATUS_FIELD).get(org);
                    UUID orgId = UUID.fromString(orgNode.get(ORG_ID_FIELD).textValue());

                    if (orgNode.get("active").booleanValue()) {
                        orgService.addOrganizationMember(orgId, Lists.newArrayList(id));
                    } else {
                        orgService.removeOrganizationMember(orgId, Lists.newArrayList(id));
                    }
                }
            }
            catch (ResourceAlreadyExistsException | RecordNotFoundException | TransactionSystemException e) {
                personIdStatus.put(id, "Problem - " + airman.getFullName() + " (" + e.getMessage() + ")");
            }
        }

        return personIdStatus;
    }

    /**
     * Service method that takes rank, organization, and personnel information from puckboard and processes it for
     * insertion/update in Common API.
     * @param orgs JsonNode containing the "/organizations" dump from Puckboard
     * @param people JsonNode containing the "/personnel" dump from Puckboard
     * @param branchInfoJson JsonNode containing the "/branch" dump (contains rank info) from Puckboard
     * @return Map summarizing the result of the Puckboard to Common API operation.  Structure has keys: "orgs", "people",
     * and will contain each a hash stating the UUID of the entity that was persisted/updated or if there was an error
     * with that UUID.
     */
    @Override
    public Map<String, Map<UUID, String>> persistOrgsAndMembers(
            JsonNode orgs,
            JsonNode people,
            JsonNode branchInfoJson) {

        // process branch information into lookup hash
        Map<String, String> branchLookup = this.processBranchInfo(branchInfoJson);

        // process unit information
        Map<UUID, String> orgStatus = this.processOrgInformation(orgs);

        // process people information
        Map<UUID, String> peopleStatus = this.processPersonnelInfo(people, branchLookup);

        return new ImmutableMap.Builder<String, Map<UUID, String>>()
                .put("orgs", orgStatus)
                .put("people", peopleStatus)
                .build();
    }
}

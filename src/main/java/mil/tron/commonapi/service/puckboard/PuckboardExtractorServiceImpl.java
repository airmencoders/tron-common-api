package mil.tron.commonapi.service.puckboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import mil.tron.commonapi.dto.OrganizationDto;
import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.orgtypes.Unit;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.OrganizationRepository;
import mil.tron.commonapi.service.OrganizationService;
import mil.tron.commonapi.service.PersonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionSystemException;

import java.util.*;

@Service
public class PuckboardExtractorServiceImpl implements PuckboardExtractorService {

    @Autowired
    private OrganizationService orgService;

    @Autowired
    private OrganizationRepository orgRepo;

    @Autowired
    private PersonService personService;

    private static final String ARRAY_RESULT_FIELD = "result";

    private static final String ORG_ID_FIELD = "organizationId";
    private static final String ORG_TYPE_FIELD = "branchId";
    private static final String ORG_NAME_FIELD = "organizationName";
    private static final String ORG_STATUS_FIELD = "organizationStatus";

    private static final String BRANCH_ID_FIELD = "id";
    private static final String PERSON_ID_FIELD = "id";
    private static final String PERSON_FIRST_NAME_FIELD = "firstName";
    private static final String PERSON_LAST_NAME_FIELD = "lastName";
    private static final String PERSON_EMAIL_FIELD = "email";
    private static final String PERSON_DODID_FIELD = "dodId";
    private static final String PERSON_PHONE_FIELD = "contactNumber";
    private static final String PERSON_RANK_FIELD = "rankId";
    private static final String PERSON_RANK_ABBR_FIELD = "rankAbbr";
    private static final String PERSON_PRIMARY_ORG_FIELD = "primaryOrganizationId";

    private static final Branch[] branchMapping = {
            Branch.OTHER, // 0
            Branch.USA, // 1
            Branch.USMC, // 2
            Branch.USN, // 3
            Branch.USAF, // 4
            Branch.USSF, // 5
            Branch.USCG, // 6
    };

    @AllArgsConstructor
    private static class RankInfo {
        String rank;
        Branch branch;
    }

    /**
     * Convert the JSON as given by the controller (as a JsonNode)
     * so that we can parse through it and build a rankId -> rankAbbr
     * lookup table since Puckboard saves 'rankId' for a person's rank and
     * Common API uses a set of pre-defined rank strings.
     * Puckboard's rank entity is an array of all the services as a object, like this:
     * {
     *  "result": [
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
     *   ]
     * }
     * @param branchInfoJson The JsonNode given by the controller from the Puckboard API branch info dump
     */
    private Map<Integer, RankInfo> processBranchInfo(JsonNode branchInfoJson) {
        Map<Integer, RankInfo> allRanks = new HashMap<>();
        allRanks.put(0, new RankInfo("CIV", Branch.USAF)); //default
        for (int i = 0; i < branchInfoJson.size(); i++) {
            JsonNode node = branchInfoJson.get(i);
            Branch branch = resolveServiceName(node.get(BRANCH_ID_FIELD).asInt());
            JsonNode rankSet = node.get("ranks");
            for (int j = 0; j < rankSet.size(); j++) {
                JsonNode rankNode = rankSet.get(j);
                allRanks.put(
                        rankNode.get(PERSON_RANK_FIELD).asInt(),
                        new RankInfo(rankNode.get(PERSON_RANK_ABBR_FIELD).textValue(),branch));
            }
        }

        return allRanks;
    }

    // helper function to convert PB's branchId to service name
    private Branch resolveServiceName(Integer branchId) {
        return branchId >= 0 && branchId < branchMapping.length ? branchMapping[branchId] : Branch.USAF;
    }

    // helper function to infer unit type by its name, and if that fails use the "OTHER_*" for the branchType
    private Unit resolveUnitType(JsonNode node, Branch branchType) {
        if (node != null) {
            Optional<Unit> unit = Arrays.stream(Unit.values())
                    .filter(item -> node.textValue().toUpperCase().contains(item.toString())).findFirst();

            if (unit.isPresent()) return unit.get();
            if (branchType.equals(Branch.USA)) return Unit.OTHER_USA;
            if (branchType.equals(Branch.USN)) return Unit.OTHER_USN;
            if (branchType.equals(Branch.USCG)) return Unit.OTHER_USCG;
            if (branchType.equals(Branch.USMC)) return Unit.OTHER_USMC;
            if (branchType.equals(Branch.USSF)) return Unit.OTHER_USSF;
        }

        // something weird happened, default other usaf unit
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
                s.setBranchType(resolveServiceName(node.get(ORG_TYPE_FIELD).asInt()));
                s.setOrgType(resolveUnitType(node.get(ORG_NAME_FIELD), s.getBranchType()));
                orgService.createOrganization(s);
                unitIdStatus.put(id, "Created - " + name);
            }
            else {
                // unit exists, just update the unit name
                orgService.modify(id,
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
     * @param rankLookup Lookup table of puckboard's rank structure built from {@link #processBranchInfo(JsonNode)}
     * @return Map of personnel data added/updated to Common API by the UUID and if the entity was added/updated or had an error
     */
    private Map<UUID, String> processPersonnelInfo(JsonNode peopleInfo, Map<Integer, RankInfo> rankLookup) {

        Map<UUID, String> personIdStatus = new HashMap<>();

        // go thru each person and add/update to Common
        for (int i = 0; i < peopleInfo.size(); i++) {
            JsonNode node = peopleInfo.get(i);

            // skip placeholder people
            if (node.has("isPlaceholder") && !node.get("isPlaceholder").isNull() && node.get("isPlaceholder").asBoolean()) continue;

            PersonDto personDto = convertToPersonDto(node, rankLookup);

            try {
                if (!personService.exists(personDto.getId())) {
                    personService.createPerson(personDto);
                    personIdStatus.put(personDto.getId(), "Created - " + personDto.getFullName());
                } else {
                    personService.updatePerson(personDto.getId(), personDto);
                    personIdStatus.put(personDto.getId(), "Updated - " + personDto.getFullName());
                }

                assignPersonToOrg(node, personDto.getId());
            } catch (ResourceAlreadyExistsException | RecordNotFoundException | TransactionSystemException e) {
                personIdStatus.put(personDto.getId(), "Problem - " + personDto.getFullName() + " (" + e.getMessage() + ")");
            }
        }

        return personIdStatus;
    }

    private PersonDto convertToPersonDto(JsonNode node, Map<Integer, RankInfo> rankLookup) {
        RankInfo rankInfo = rankLookup.get(node.get(PERSON_RANK_FIELD).asInt());
        return PersonDto.builder()
                .id(UUID.fromString(node.get(PERSON_ID_FIELD).textValue()))
                .dodid(node.get(PERSON_DODID_FIELD).asText(null))
                .dutyPhone(node.get(PERSON_PHONE_FIELD).textValue())
                .title(rankInfo.rank)
                .rank(rankInfo.rank)
                .branch(rankInfo.branch)
                .firstName(node.get(PERSON_FIRST_NAME_FIELD).textValue())
                .lastName(node.get(PERSON_LAST_NAME_FIELD).textValue())
                .email(node.get(PERSON_EMAIL_FIELD).textValue())
                .build();
    }

    private void assignPersonToOrg(JsonNode node, UUID id) {
        // now go thru each puckboard org person was in - either add or remove depending on active status
        UUID primaryOrg = UUID.fromString(node.get(PERSON_PRIMARY_ORG_FIELD).textValue());
        for (JsonNode org : Lists.newArrayList(node.get(ORG_STATUS_FIELD).elements())) {
            UUID orgId = UUID.fromString(org.get(ORG_ID_FIELD).textValue());

            if (orgService.getOrganization(orgId).getMembers().contains(id)) {
                orgService.removeOrganizationMember(orgId, Lists.newArrayList(id));
            }

            if (org.get("active").booleanValue()) {
                orgService.addOrganizationMember(orgId, Lists.newArrayList(id), orgId.equals(primaryOrg));
            }
        }
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
        Map<Integer, RankInfo> rankLookup = this.processBranchInfo(branchInfoJson.get(ARRAY_RESULT_FIELD));

        // process unit information
        Map<UUID, String> orgStatus = this.processOrgInformation(orgs.get(ARRAY_RESULT_FIELD));

        // process people information
        Map<UUID, String> peopleStatus = this.processPersonnelInfo(people.get(ARRAY_RESULT_FIELD), rankLookup);

        return new ImmutableMap.Builder<String, Map<UUID, String>>()
                .put("orgs", orgStatus)
                .put("people", peopleStatus)
                .build();
    }
}

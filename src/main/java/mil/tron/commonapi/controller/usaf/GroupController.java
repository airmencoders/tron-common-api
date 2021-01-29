package mil.tron.commonapi.controller.usaf;

import mil.tron.commonapi.dto.OrganizationDto;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.orgtypes.Unit;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.service.OrganizationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * USAF Group-level facade for Organizations...only deals with orgs of type "Unit.GROUP", otherwise it will error out
 */
@RestController
@RequestMapping("${api-prefix.v1}/group")
public class GroupController {
    private OrganizationService organizationService;
    private static final String UNIT_NOT_A_GROUP = "Organization type given was not a Group";

    public GroupController(OrganizationService organizationService) { this.organizationService = organizationService; }

    // TODO: add swagger doc
    @GetMapping("")
    public ResponseEntity<Object> getAllGroupTypes() {
        return new ResponseEntity<>(organizationService.getOrganizationsByTypeAndService(Unit.GROUP, Branch.USAF), HttpStatus.OK);
    }

    // TODO: add swagger doc
    @GetMapping("/{id}")
    public ResponseEntity<Object> getGroupById(@PathVariable UUID id) {
        OrganizationDto org = organizationService.getOrganization(id);
        if (org.getOrgType().equals(Unit.GROUP))
            return new ResponseEntity<>(org, HttpStatus.OK);
        else
            throw new RecordNotFoundException("A unit exists by that ID but it is not a Group");
    }

    // TODO: add swagger doc
    @PostMapping("")
    public ResponseEntity<Object> createNewGroup(@RequestBody OrganizationDto org) {
        org.setOrgType(Unit.GROUP);  // force type to group
        org.setBranchType(Branch.USAF); // force branch to USAF
        return new ResponseEntity<>(organizationService.createOrganization(org), HttpStatus.CREATED);
    }

    // TODO: add swagger doc
    @PutMapping("/{id}")
    public ResponseEntity<Object> updateGroup(@PathVariable UUID id, @RequestBody OrganizationDto org) {
        if (org.getOrgType().equals(Unit.GROUP)) {
            OrganizationDto updatedOrg = organizationService.updateOrganization(id, org);

            if (updatedOrg != null)
                return new ResponseEntity<>(updatedOrg, HttpStatus.OK);
            else
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        else
            throw new InvalidRecordUpdateRequest(GroupController.UNIT_NOT_A_GROUP);
    }

    // TODO: add swagger doc
    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Object> deleteGroup(@PathVariable UUID id) {
        OrganizationDto org = organizationService.getOrganization(id);

        if (org.getOrgType().equals(Unit.GROUP)) {
            organizationService.deleteOrganization(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
        else {
            throw new InvalidRecordUpdateRequest(GroupController.UNIT_NOT_A_GROUP);
        }
    }

    // TODO: add swagger doc
    @DeleteMapping("/{id}/members")
    public ResponseEntity<Object> deleteGroupMembers(@PathVariable UUID id, @RequestBody List<UUID> personId) {

        OrganizationDto org = organizationService.getOrganization(id);
        if (org.getOrgType().equals(Unit.GROUP))
            return new ResponseEntity<>(organizationService.removeOrganizationMember(id, personId), HttpStatus.OK);
        else
            throw new InvalidRecordUpdateRequest(GroupController.UNIT_NOT_A_GROUP);
    }

    // TODO: add swagger doc
    @PatchMapping("/{id}/members")
    public ResponseEntity<Object> addGroupMembers(@PathVariable UUID id, @RequestBody List<UUID> personId) {

        OrganizationDto org = organizationService.getOrganization(id);
        if (org.getOrgType().equals(Unit.GROUP))
            return new ResponseEntity<>(organizationService.addOrganizationMember(id, personId), HttpStatus.OK);
        else
            throw new InvalidRecordUpdateRequest(GroupController.UNIT_NOT_A_GROUP);
    }

    // TODO: add swagger doc
    @PatchMapping(value = "/{id}")
    public ResponseEntity<OrganizationDto> patchGroup(@PathVariable UUID id, @RequestBody Map<String, String> attribs) {
        OrganizationDto org = organizationService.getOrganization(id);
        if (org.getOrgType().equals(Unit.GROUP))
            return new ResponseEntity<>(organizationService.modifyAttributes(id, attribs), HttpStatus.OK);
        else
            throw new InvalidRecordUpdateRequest(GroupController.UNIT_NOT_A_GROUP);
    }

    // TODO: add swagger doc
    @PostMapping(value = "/groups")
    public ResponseEntity<Object> addNewGroups(@RequestBody List<OrganizationDto> orgs) {
        for (OrganizationDto newOrg : orgs) {
            if (!newOrg.getOrgType().equals(Unit.GROUP))
                throw new InvalidRecordUpdateRequest("One or more provided units were not of type Group");
        }

        return new ResponseEntity<>(organizationService.bulkAddOrgs(orgs), HttpStatus.CREATED);
    }
}

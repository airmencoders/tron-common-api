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
 * USAF-Squadron level facade for organizations...only deals with orgs of type "Unit.SQUADRON", otherwise it will error out
 */
@RestController
@RequestMapping("${api-prefix.v1}/squadron")
public class SquadronController {
    private OrganizationService organizationService;
    private static final String UNIT_NOT_A_SQUADRON = "Organization type given was not a Squadron";

    public SquadronController(OrganizationService organizationService) { this.organizationService = organizationService; }

    // TODO: add swagger doc
    @GetMapping("")
    public ResponseEntity<Object> getAllSquadronTypes() {
        return new ResponseEntity<>(organizationService.getOrganizationsByType(Unit.SQUADRON), HttpStatus.OK);
    }

    // TODO: add swagger doc
    @GetMapping("/{id}")
    public ResponseEntity<Object> getSquadronById(@PathVariable UUID id) {
        OrganizationDto org = organizationService.getOrganization(id);
        if (org.getOrgType().equals(Unit.SQUADRON))
            return new ResponseEntity<>(org, HttpStatus.OK);
        else
            throw new RecordNotFoundException("A unit exists by that ID but it is not a Squadron");
    }

    // TODO: add swagger doc
    @PostMapping("")
    public ResponseEntity<Object> createNewSquadron(@RequestBody OrganizationDto org) {
        org.setOrgType(Unit.SQUADRON);  // force type to squadron
        org.setBranchType(Branch.USAF); // force branch to USAF
        return new ResponseEntity<>(organizationService.createOrganization(org), HttpStatus.CREATED);
    }

    // TODO: add swagger doc
    @PutMapping("/{id}")
    public ResponseEntity<Object> updateSquadron(@PathVariable UUID id, @RequestBody OrganizationDto org) {
        if (org.getOrgType().equals(Unit.SQUADRON)) {
            OrganizationDto updatedOrg = organizationService.updateOrganization(id, org);

            if (updatedOrg != null)
                return new ResponseEntity<>(updatedOrg, HttpStatus.OK);
            else
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        else
            throw new InvalidRecordUpdateRequest(SquadronController.UNIT_NOT_A_SQUADRON);
    }

    // TODO: add swagger doc
    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Object> deleteSquadron(@PathVariable UUID id) {
        OrganizationDto org = organizationService.getOrganization(id);

        if (org.getOrgType().equals(Unit.SQUADRON)) {
            organizationService.deleteOrganization(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
        else {
            throw new InvalidRecordUpdateRequest(SquadronController.UNIT_NOT_A_SQUADRON);
        }
    }

    // TODO: add swagger doc
    @DeleteMapping("/{id}/members")
    public ResponseEntity<Object> deleteSquadronMembers(@PathVariable UUID id, @RequestBody List<UUID> personId) {

        OrganizationDto org = organizationService.getOrganization(id);
        if (org.getOrgType().equals(Unit.SQUADRON))
            return new ResponseEntity<>(organizationService.removeOrganizationMember(id, personId), HttpStatus.OK);
        else
            throw new InvalidRecordUpdateRequest(SquadronController.UNIT_NOT_A_SQUADRON);
    }

    // TODO: add swagger doc
    @PatchMapping("/{id}/members")
    public ResponseEntity<Object> addSquadronMembers(@PathVariable UUID id, @RequestBody List<UUID> personId) {

        OrganizationDto org = organizationService.getOrganization(id);
        if (org.getOrgType().equals(Unit.SQUADRON))
            return new ResponseEntity<>(organizationService.addOrganizationMember(id, personId), HttpStatus.OK);
        else
            throw new InvalidRecordUpdateRequest(SquadronController.UNIT_NOT_A_SQUADRON);
    }

    // TODO: add swagger doc
    @PatchMapping(value = "/{id}")
    public ResponseEntity<OrganizationDto> patchSquadron(@PathVariable UUID id, @RequestBody Map<String, String> attribs) {
        OrganizationDto org = organizationService.getOrganization(id);
        if (org.getOrgType().equals(Unit.SQUADRON))
            return new ResponseEntity<>(organizationService.modifyAttributes(id, attribs), HttpStatus.OK);
        else
            throw new InvalidRecordUpdateRequest(SquadronController.UNIT_NOT_A_SQUADRON);
    }

    // TODO: add swagger doc
    @PostMapping(value = "/squadrons")
    public ResponseEntity<Object> addNewSquadrons(@RequestBody List<OrganizationDto> orgs) {
        for (OrganizationDto newOrg : orgs) {
            if (!newOrg.getOrgType().equals(Unit.SQUADRON))
                throw new InvalidRecordUpdateRequest("One or more provided units were not of type Squadron");
        }

        return new ResponseEntity<>(organizationService.bulkAddOrgs(orgs), HttpStatus.CREATED);
    }

}

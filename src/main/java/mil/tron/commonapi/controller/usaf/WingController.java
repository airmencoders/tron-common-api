package mil.tron.commonapi.controller.usaf;

import mil.tron.commonapi.dto.OrganizationDto;
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
 * USAF-Wing level facade for organizations...only deals with orgs of type "Unit.WING", otherwise it will error out
 */
@RestController
@RequestMapping("${api-prefix.v1}/wing")
public class WingController {

    private OrganizationService organizationService;
    private static final String UNIT_NOT_A_WING = "Organization type given was not a Wing";

    public WingController(OrganizationService organizationService) { this.organizationService = organizationService; }

    // TODO: add swagger doc
    @GetMapping("")
    public ResponseEntity<Object> getAllWingTypes() {
        return new ResponseEntity<>(organizationService.getOrganizationsByType(Unit.WING), HttpStatus.OK);
    }

    // TODO: add swagger doc
    @GetMapping("/{id}")
    public ResponseEntity<Object> getWingById(@PathVariable UUID id) {
        OrganizationDto org = organizationService.getOrganization(id);
        if (org.getOrgType().equals(Unit.WING))
            return new ResponseEntity<>(org, HttpStatus.OK);
        else
            throw new RecordNotFoundException("A unit exists by that ID but it is not a Wing");
    }

    // TODO: add swagger doc
    @PostMapping("")
    public ResponseEntity<Object> createNewWing(@RequestBody OrganizationDto org) {
        org.setOrgType(Unit.WING);  // force type to wing
        return new ResponseEntity<>(organizationService.createOrganization(org), HttpStatus.CREATED);
    }

    // TODO: add swagger doc
    @PutMapping("/{id}")
    public ResponseEntity<Object> updateWing(@PathVariable UUID id, @RequestBody OrganizationDto org) {
        if (org.getOrgType().equals(Unit.WING)) {
            OrganizationDto updatedOrg = organizationService.updateOrganization(id, org);

            if (updatedOrg != null)
                return new ResponseEntity<>(updatedOrg, HttpStatus.OK);
            else
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        else
            throw new InvalidRecordUpdateRequest(WingController.UNIT_NOT_A_WING);
    }

    // TODO: add swagger doc
    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Object> deleteWing(@PathVariable UUID id) {
        OrganizationDto org = organizationService.getOrganization(id);

        if (org.getOrgType().equals(Unit.WING)) {
            organizationService.deleteOrganization(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
        else {
            throw new InvalidRecordUpdateRequest(WingController.UNIT_NOT_A_WING);
        }
    }

    // TODO: add swagger doc
    @DeleteMapping("/{id}/members")
    public ResponseEntity<Object> deleteWingMembers(@PathVariable UUID id, @RequestBody List<UUID> personId) {

        OrganizationDto org = organizationService.getOrganization(id);
        if (org.getOrgType().equals(Unit.WING))
            return new ResponseEntity<>(organizationService.removeOrganizationMember(id, personId), HttpStatus.OK);
        else
            throw new InvalidRecordUpdateRequest(WingController.UNIT_NOT_A_WING);
    }

    // TODO: add swagger doc
    @PatchMapping("/{id}/members")
    public ResponseEntity<Object> addWingMembers(@PathVariable UUID id, @RequestBody List<UUID> personId) {

        OrganizationDto org = organizationService.getOrganization(id);
        if (org.getOrgType().equals(Unit.WING))
            return new ResponseEntity<>(organizationService.addOrganizationMember(id, personId), HttpStatus.OK);
        else
            throw new InvalidRecordUpdateRequest(WingController.UNIT_NOT_A_WING);
    }

    // TODO: add swagger doc
    @PatchMapping(value = "/{id}")
    public ResponseEntity<OrganizationDto> patchWing(@PathVariable UUID id, @RequestBody Map<String, String> attribs) {
        OrganizationDto org = organizationService.getOrganization(id);
        if (org.getOrgType().equals(Unit.WING))
            return new ResponseEntity<>(organizationService.modifyAttributes(id, attribs), HttpStatus.OK);
        else
            throw new InvalidRecordUpdateRequest(WingController.UNIT_NOT_A_WING);
    }

    // TODO: add swagger doc
    @PostMapping(value = "/wings")
    public ResponseEntity<Object> addNewWings(@RequestBody List<OrganizationDto> orgs) {
        for (OrganizationDto newOrg : orgs) {
            if (!newOrg.getOrgType().equals(Unit.WING))
                throw new InvalidRecordUpdateRequest("One or more provided units were not of type Wing");
        }

        return new ResponseEntity<>(organizationService.bulkAddOrgs(orgs), HttpStatus.CREATED);
    }
}

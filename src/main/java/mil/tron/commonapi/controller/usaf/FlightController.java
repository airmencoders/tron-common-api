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
 * USAF-Flight level facade for organizations...only deals with orgs of type "Unit.FLIGHT", otherwise it will error out
 */
@RestController
@RequestMapping("${api-prefix.v1}/flight")
public class FlightController {
    private OrganizationService organizationService;
    private static final String UNIT_NOT_A_FLIGHT = "Organization type given was not a Flight";

    public FlightController(OrganizationService organizationService) { this.organizationService = organizationService; }

    // TODO: add swagger doc
    @GetMapping("")
    public ResponseEntity<Object> getAllFlightTypes() {
        return new ResponseEntity<>(organizationService.getOrganizationsByType(Unit.FLIGHT), HttpStatus.OK);
    }

    // TODO: add swagger doc
    @GetMapping("/{id}")
    public ResponseEntity<Object> getFlightById(@PathVariable UUID id) {
        OrganizationDto org = organizationService.getOrganization(id);
        if (org.getOrgType().equals(Unit.FLIGHT))
            return new ResponseEntity<>(org, HttpStatus.OK);
        else
            throw new RecordNotFoundException("A unit exists by that ID but it is not a Flight");
    }

    // TODO: add swagger doc
    @PostMapping("")
    public ResponseEntity<Object> createNewFlight(@RequestBody OrganizationDto org) {
        org.setOrgType(Unit.FLIGHT);  // force type to flight
        org.setBranchType(Branch.USAF); // force branch to USAF
        return new ResponseEntity<>(organizationService.createOrganization(org), HttpStatus.CREATED);
    }

    // TODO: add swagger doc
    @PutMapping("/{id}")
    public ResponseEntity<Object> updateFlight(@PathVariable UUID id, @RequestBody OrganizationDto org) {
        if (org.getOrgType().equals(Unit.FLIGHT)) {
            OrganizationDto updatedOrg = organizationService.updateOrganization(id, org);

            if (updatedOrg != null)
                return new ResponseEntity<>(updatedOrg, HttpStatus.OK);
            else
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        else
            throw new InvalidRecordUpdateRequest(FlightController.UNIT_NOT_A_FLIGHT);
    }

    // TODO: add swagger doc
    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Object> deleteFlight(@PathVariable UUID id) {
        OrganizationDto org = organizationService.getOrganization(id);

        if (org.getOrgType().equals(Unit.FLIGHT)) {
            organizationService.deleteOrganization(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
        else {
            throw new InvalidRecordUpdateRequest(FlightController.UNIT_NOT_A_FLIGHT);
        }
    }

    // TODO: add swagger doc
    @DeleteMapping("/{id}/members")
    public ResponseEntity<Object> deleteFlightMembers(@PathVariable UUID id, @RequestBody List<UUID> personId) {

        OrganizationDto org = organizationService.getOrganization(id);
        if (org.getOrgType().equals(Unit.FLIGHT))
            return new ResponseEntity<>(organizationService.removeOrganizationMember(id, personId), HttpStatus.OK);
        else
            throw new InvalidRecordUpdateRequest(FlightController.UNIT_NOT_A_FLIGHT);
    }

    // TODO: add swagger doc
    @PatchMapping("/{id}/members")
    public ResponseEntity<Object> addFlightMembers(@PathVariable UUID id, @RequestBody List<UUID> personId) {

        OrganizationDto org = organizationService.getOrganization(id);
        if (org.getOrgType().equals(Unit.FLIGHT))
            return new ResponseEntity<>(organizationService.addOrganizationMember(id, personId), HttpStatus.OK);
        else
            throw new InvalidRecordUpdateRequest(FlightController.UNIT_NOT_A_FLIGHT);
    }

    // TODO: add swagger doc
    @PatchMapping(value = "/{id}")
    public ResponseEntity<OrganizationDto> patchFlight(@PathVariable UUID id, @RequestBody Map<String, String> attribs) {
        OrganizationDto org = organizationService.getOrganization(id);
        if (org.getOrgType().equals(Unit.FLIGHT))
            return new ResponseEntity<>(organizationService.modifyAttributes(id, attribs), HttpStatus.OK);
        else
            throw new InvalidRecordUpdateRequest(FlightController.UNIT_NOT_A_FLIGHT);
    }

    // TODO: add swagger doc
    @PostMapping(value = "/flights")
    public ResponseEntity<Object> addNewFlights(@RequestBody List<OrganizationDto> orgs) {
        for (OrganizationDto newOrg : orgs) {
            if (!newOrg.getOrgType().equals(Unit.FLIGHT))
                throw new InvalidRecordUpdateRequest("One or more provided units were not of type Flight");
        }

        return new ResponseEntity<>(organizationService.bulkAddOrgs(orgs), HttpStatus.CREATED);
    }
    
}

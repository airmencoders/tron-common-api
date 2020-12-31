package mil.tron.commonapi.service.utility;

import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.OrganizationRepository;
import org.springframework.stereotype.Service;

@Service
public class OrganizationUniqueChecksServiceImpl implements OrganizationUniqueChecksService {

    private OrganizationRepository orgRepo;

    public OrganizationUniqueChecksServiceImpl(OrganizationRepository orgRepo) {
        this.orgRepo = orgRepo;
    }

    /**
     * Organizational Unique Name Constraint Check.
     * Validates that the organization name is unique amongst all other entities of any Organization type
     */
    @Override
    public boolean orgNameIsUnique(Organization org) {

        if (org.getId() != null && orgRepo.existsById(org.getId())) {

            // check uniqueness against an already existing org entity

            Organization dbOrg = orgRepo.findById(org.getId()).orElseThrow(() ->
                    new RecordNotFoundException("Error retrieving Record with UUID: " + org.getId())
            );

            /**
             * Unique Name Check
             *
             * First check if there is a name change update.
             * If there is a name change, look in the database
             * for any organization that is using the new name.
             * Throw exception if an organization with the new name
             * already exists.
             */
            String orgName = org.getName();
            return (orgName == null || orgName.equalsIgnoreCase(dbOrg.getName()) || orgRepo.findByNameIgnoreCase(orgName).isEmpty());

        }  else {

            // check uniqueness against a new org entity
            return (org.getName() == null || orgRepo.findByNameIgnoreCase(org.getName()).isEmpty());
        }
    }
}

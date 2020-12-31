package mil.tron.commonapi.service.utility;


import mil.tron.commonapi.entity.Organization;

public interface OrganizationUniqueChecksService {

    boolean orgNameIsUnique(Organization org);

}

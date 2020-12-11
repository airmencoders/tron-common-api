package mil.tron.commonapi.repository;

import java.util.UUID;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import mil.tron.commonapi.organization.Organization;

@Repository
public interface OrganizationRepository extends CrudRepository<Organization, UUID> {

}

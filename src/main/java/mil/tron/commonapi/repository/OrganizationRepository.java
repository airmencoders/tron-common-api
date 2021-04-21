package mil.tron.commonapi.repository;

import mil.tron.commonapi.entity.Organization;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationRepository extends CrudRepository<Organization, UUID> {
	Optional<Organization> findByNameIgnoreCase(String name);

	List<Organization> findOrganizationsByParentOrganization(Organization org);
	List<Organization> findOrganizationsBySubordinateOrganizationsContaining(Organization org);

	@Transactional
	List<Organization> deleteByLeaderId(UUID id);
}

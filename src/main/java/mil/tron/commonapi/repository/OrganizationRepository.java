package mil.tron.commonapi.repository;

import mil.tron.commonapi.entity.Organization;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
	Optional<Organization> findByNameIgnoreCase(String name);

	List<Organization> findOrganizationsByParentOrganization(Organization org);
	List<Organization> findOrganizationsBySubordinateOrganizationsContaining(Organization org);

	@Transactional
	List<Organization> deleteByLeaderId(UUID id);
	
	Slice<Organization> findBy(Pageable page);
}

package mil.tron.commonapi.repository;

import mil.tron.commonapi.entity.Organization;

import mil.tron.commonapi.entity.Person;
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

	List<Organization> findOrganizationsByLeader(Person leader);
	
	Slice<Organization> findBy(Pageable page);
}

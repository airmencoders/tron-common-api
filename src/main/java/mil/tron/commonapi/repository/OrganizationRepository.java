package mil.tron.commonapi.repository;

import mil.tron.commonapi.entity.Organization;
import mil.tron.commonapi.entity.Person;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.orgtypes.Unit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
	Optional<Organization> findByNameIgnoreCase(String name);

	List<Organization> findOrganizationsByParentOrganization(Organization org);
	List<Organization> findOrganizationsBySubordinateOrganizationsContaining(Organization org);

	List<Organization> findOrganizationsByLeader(Person leader);
	
	Slice<Organization> findBy(Pageable pageable);
	Slice<Organization> findByNameContainsIgnoreCase(String name, Pageable pageable);
	Slice<Organization> findByNameContainsIgnoreCaseAndOrgTypeAndBranchType(String name, Unit unit, Branch branch, Pageable pageable);
	Slice<Organization> findByNameContainsIgnoreCaseAndOrgType(String name, Unit unit, Pageable pageable);
	Slice<Organization> findByNameContainsIgnoreCaseAndBranchType(String name, Branch branch, Pageable pageable);
	
	Page<Organization> findAll(Pageable pageable);
	Page<Organization> findAllByNameContainsIgnoreCase(String name, Pageable pageable);
	Page<Organization> findAllByNameContainsIgnoreCaseAndOrgTypeAndBranchType(String name, Unit unit, Branch branch, Pageable pageable);
	Page<Organization> findAllByNameContainsIgnoreCaseAndOrgType(String name, Unit unit, Pageable pageable);
	Page<Organization> findAllByNameContainsIgnoreCaseAndBranchType(String name, Branch branch, Pageable pageable);
}

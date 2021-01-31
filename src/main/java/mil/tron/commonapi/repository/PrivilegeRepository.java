package mil.tron.commonapi.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import mil.tron.commonapi.entity.Privilege;

@Repository
public interface PrivilegeRepository extends CrudRepository<Privilege, Long> {
	
}

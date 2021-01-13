package mil.tron.commonapi.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import mil.tron.commonapi.entity.Role;

@Repository
public interface RoleRepository extends CrudRepository<Role, Long> {
	
}

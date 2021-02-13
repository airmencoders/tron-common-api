package mil.tron.commonapi.repository;

import mil.tron.commonapi.entity.Person;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import mil.tron.commonapi.entity.Privilege;

import java.util.Optional;

@Repository
public interface PrivilegeRepository extends CrudRepository<Privilege, Long> {
    public Optional<Privilege> findByName(String name);
}

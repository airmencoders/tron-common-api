package mil.tron.commonapi.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import mil.tron.commonapi.entity.User;

@Repository
public interface UserRepository extends CrudRepository<User, UUID> {
	public Optional<User> findByNameIgnoreCase(String name);
}

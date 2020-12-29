package mil.tron.commonapi.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import mil.tron.commonapi.entity.Person;

@Repository
public interface PersonRepository extends CrudRepository<Person, UUID> {
	public Optional<Person> findByEmailIgnoreCase(String email);
}

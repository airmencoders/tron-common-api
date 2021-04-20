package mil.tron.commonapi.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import mil.tron.commonapi.entity.Person;

@Repository
public interface PersonRepository extends JpaRepository<Person, UUID> {
	public Optional<Person> findByEmailIgnoreCase(String email);
	public Optional<Person> findByDodidIgnoreCase(String dodid);
}

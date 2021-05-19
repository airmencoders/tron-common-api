package mil.tron.commonapi.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import mil.tron.commonapi.entity.Person;

@Repository
public interface PersonRepository extends JpaRepository<Person, UUID> {
	Optional<Person> findByEmailIgnoreCase(String email);
	Optional<Person> findByDodidIgnoreCase(String dodid);
	Slice<Person> findBy(Pageable pageable);
	Page<Person> findAll(Pageable pageable);
}

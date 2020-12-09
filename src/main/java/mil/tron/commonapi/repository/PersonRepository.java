package mil.tron.commonapi.repository;

import java.util.UUID;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import mil.tron.commonapi.person.Person;

@Repository
public interface PersonRepository extends CrudRepository<Person, UUID> {

}

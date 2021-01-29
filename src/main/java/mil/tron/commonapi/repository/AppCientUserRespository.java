package mil.tron.commonapi.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import mil.tron.commonapi.entity.AppClientUser;

@Repository
public interface AppCientUserRespository extends CrudRepository<AppClientUser, UUID> {
	public Optional<AppClientUser> findByNameIgnoreCase(String name);
}

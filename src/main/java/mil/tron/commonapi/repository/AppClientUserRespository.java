package mil.tron.commonapi.repository;

import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.DashboardUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppClientUserRespository extends JpaRepository<AppClientUser, UUID> {
	Optional<AppClientUser> findByNameIgnoreCase(String name);
	List<AppClientUser> findByAppClientDevelopersContaining(DashboardUser user);
}

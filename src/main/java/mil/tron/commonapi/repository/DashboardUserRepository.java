package mil.tron.commonapi.repository;

import mil.tron.commonapi.entity.DashboardUser;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DashboardUserRepository extends CrudRepository<DashboardUser, UUID> {
    public Optional<DashboardUser> findByEmailIgnoreCase(String name);
}

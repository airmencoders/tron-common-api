package mil.tron.commonapi.repository;

import mil.tron.commonapi.entity.DashboardUser;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DashboardUserRepository extends CrudRepository<DashboardUser, UUID> {
    Optional<DashboardUser> findByEmailIgnoreCase(String name);
    Page<DashboardUser> findAllByDocumentSpaces_Id(UUID documentSpaceId, Pageable pageable);
}

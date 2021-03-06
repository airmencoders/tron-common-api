package mil.tron.commonapi.repository;

import mil.tron.commonapi.entity.DashboardUser;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DashboardUserRepository extends CrudRepository<DashboardUser, UUID> {
    Optional<DashboardUser> findByEmailIgnoreCase(String name);
    Optional<DashboardUser> findByEmailAsLower(String email);
    Page<DashboardUser> findAllByDocumentSpaces_Id(UUID documentSpaceId, Pageable pageable);

    @Transactional
    @Modifying
    @Query("update DashboardUser d set d.defaultDocumentSpaceId = null where d.defaultDocumentSpaceId = :documentSpaceId")
    void unsetDashboardUsersDefaultDocumentSpaceForDocumentSpace(@Param(value = "documentSpaceId") UUID documentSpaceId);
}

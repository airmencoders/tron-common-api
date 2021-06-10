package mil.tron.commonapi.repository;

import mil.tron.commonapi.entity.Privilege;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.Optional;

@Repository
public interface PrivilegeRepository extends JpaRepository<Privilege, Long> {
    Optional<Privilege> findByName(String name);

    @Modifying
    @Transactional
    @Query(value = "insert into privilege (name) values :name", nativeQuery = true)
    void createPrivilege(String name);

    @Modifying
    @Transactional
    @Query(value = "delete from app_privileges where privileges_id = :id", nativeQuery = true)
    void deletePrivilegeFromAppClients(Long id);

    @Modifying
    @Transactional
    @Query(value = "delete from dashboard_user_privileges where privileges_id = :id", nativeQuery = true)
    void deletePrivilegeFromDashboardUsers(Long id);

    @Modifying
    @Transactional
    @Query(value = "delete from privilege where id = :id", nativeQuery = true)
    void deletePrivilegeById(Long id);
}

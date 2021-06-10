package mil.tron.commonapi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import mil.tron.commonapi.entity.Privilege;

import javax.transaction.Transactional;
import java.util.Optional;

@Repository
public interface PrivilegeRepository extends JpaRepository<Privilege, Long> {
    Optional<Privilege> findByName(String name);

    @Modifying
    @Transactional
    @Query(value = "insert into privilege (name) values :name", nativeQuery = true)
    void createPrivilege(String name);
}

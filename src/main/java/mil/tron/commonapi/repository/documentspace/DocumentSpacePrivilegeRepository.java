package mil.tron.commonapi.repository.documentspace;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import mil.tron.commonapi.entity.documentspace.DocumentSpacePrivilege;

@Repository
public interface DocumentSpacePrivilegeRepository extends JpaRepository<DocumentSpacePrivilege, UUID> {
	Optional<DocumentSpacePrivilege> findByName(String name);
}

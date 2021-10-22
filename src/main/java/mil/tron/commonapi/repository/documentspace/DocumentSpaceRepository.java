package mil.tron.commonapi.repository.documentspace;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import mil.tron.commonapi.entity.documentspace.DocumentSpace;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentSpaceRepository extends CrudRepository<DocumentSpace, UUID> {
	<T> List<T> findAllDynamicBy(Class<T> type);
	<T> List<T> findAllDynamicByDashboardUsers_Id(UUID dashboardUserId, Class<T> type);
	boolean existsByName(String name);

	@Query(value = "select count(document_space_id) > 0 " +
			"from document_space_dashboard_users " +
			"where document_space_id = :documentSpaceId " +
			"and dashboard_user_id = :dashboardUserId", nativeQuery = true)
	boolean isUserInDocumentSpace(UUID dashboardUserId, UUID documentSpaceId);
}

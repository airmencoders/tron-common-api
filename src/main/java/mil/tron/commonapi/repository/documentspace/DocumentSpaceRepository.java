package mil.tron.commonapi.repository.documentspace;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.CrudRepository;

import mil.tron.commonapi.entity.documentspace.DocumentSpace;


public interface DocumentSpaceRepository extends CrudRepository<DocumentSpace, UUID> {
	<T> List<T> findAllDynamicBy(Class<T> type);
	<T> List<T> findAllDynamicByDashboardUsers_Id(UUID dashboardUserId, Class<T> type);
	boolean existsByName(String name);
}

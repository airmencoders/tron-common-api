package mil.tron.commonapi.repository.documentspace;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import mil.tron.commonapi.entity.documentspace.DocumentSpaceDashboardMemberPrivilegeRow;
import mil.tron.commonapi.entity.documentspace.DocumentSpacePrivilege;

@Repository
public interface DocumentSpacePrivilegeRepository extends JpaRepository<DocumentSpacePrivilege, UUID> {
	Optional<DocumentSpacePrivilege> findByName(String name);
	
	List<DocumentSpacePrivilege> findAllByDocumentSpace_Id(UUID documentSpaceId);

	/**
     * Retrieves all rows of Privileges belonging to the DocumentSpace that has an
     * association with one of the provided {@link dashboardUserIds}.
     * The returned rows have been filtered such that only the privileges
     * and dashboard members associated with the specified Document Space are returned.
     * 
	 * @param documentSpaceId the Document Space ID to fetch off of
	 * @param dashboardUserIds the dashboard user ids to include in the search
	 * @return a list of all the Dashboard Users & Document Space Privileges associated rows for a Document Space
	 */
	@Query("select new mil.tron.commonapi.entity.documentspace.DocumentSpaceDashboardMemberPrivilegeRow(dashboardUser.id, dashboardUser.email, privilege)"
		+ " from DocumentSpacePrivilege privilege"
		+ " join privilege.documentSpace documentSpace"
		+ " join privilege.dashboardUsers dashboardUser"
		+ " where documentSpace.id = :documentSpaceId and dashboardUser.id in :dashboardUserIds")
	List<DocumentSpaceDashboardMemberPrivilegeRow> findAllDashboardMemberPrivilegesBelongingToDocumentSpace(UUID documentSpaceId, Set<UUID> dashboardUserIds);
}

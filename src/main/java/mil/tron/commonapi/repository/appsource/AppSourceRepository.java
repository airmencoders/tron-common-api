package mil.tron.commonapi.repository.appsource;

import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.appsource.AppSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppSourceRepository extends JpaRepository<AppSource, UUID>, AppSourceRepositoryCustom {
    boolean existsByNameIgnoreCase(String name);
    boolean existsByAppSourcePath(String path);
    boolean existsByIdAndAvailableAsAppSourceTrue(UUID id);
    AppSource findByAppSourcePath(String appSourcePath);
    Optional<AppSource> findByNameIgnoreCase(String name);
    Optional<AppSource> findByAppPrivs_Id(UUID id);
    List<AppSource> findAppSourcesByAppSourceAdminsContaining(DashboardUser user);
    List<AppSource> findByAvailableAsAppSourceTrue();
    Optional<Long> countByAvailableAsAppSourceTrue();
}

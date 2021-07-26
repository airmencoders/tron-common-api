package mil.tron.commonapi.repository.appsource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.appsource.AppSource;

public interface AppSourceRepository extends JpaRepository<AppSource, UUID>, AppSourceRepositoryCustom {
    boolean existsByNameIgnoreCase(String name);
    boolean existsByIdAndAvailableAsAppSourceTrue(UUID id);
    AppSource findByAppSourcePath(String appSourcePath);
    Optional<AppSource> findByNameIgnoreCase(String name);
    Optional<AppSource> findByAppPrivs_Id(UUID id);
    List<AppSource> findAppSourcesByAppSourceAdminsContaining(DashboardUser user);
    List<AppSource> findByAvailableAsAppSourceTrue();
    Optional<Long> countByAvailableAsAppSourceTrue();
}

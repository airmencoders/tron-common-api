package mil.tron.commonapi.repository.appsource;

import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.appsource.AppSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AppSourceRepository extends JpaRepository<AppSource, UUID> {
    boolean existsByNameIgnoreCase(String name);
    List<AppSource> findAppSourcesByAppSourceAdminsContaining(DashboardUser user);
}

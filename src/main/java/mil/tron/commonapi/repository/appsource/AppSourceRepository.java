package mil.tron.commonapi.repository.appsource;

import mil.tron.commonapi.entity.appsource.AppSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AppSourceRepository extends JpaRepository<AppSource, UUID> {
}

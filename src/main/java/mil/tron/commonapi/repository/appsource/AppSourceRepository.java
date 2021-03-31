package mil.tron.commonapi.repository.appsource;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import mil.tron.commonapi.entity.appsource.AppSource;

public interface AppSourceRepository extends JpaRepository<AppSource, UUID> {
    boolean existsByNameIgnoreCase(String name);
    AppSource findByNameIgnoreCase(String name);
}

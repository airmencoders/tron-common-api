package mil.tron.commonapi.repository.appsource;

import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.entity.appsource.AppSourcePriv;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface AppSourcePrivRepository extends CrudRepository<AppSourcePriv, UUID> {
    Iterable<AppSourcePriv> findAllByAppSource(AppSource appSource);

    Iterable<UUID> removeAllByAppSource(AppSource appSource);
}

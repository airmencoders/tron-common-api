package mil.tron.commonapi.repository.appsource;

import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.entity.appsource.AppSourcePriv;
import org.springframework.data.repository.CrudRepository;

import javax.transaction.Transactional;
import java.util.UUID;

public interface AppSourcePrivRepository extends CrudRepository<AppSourcePriv, UUID> {
    Iterable<AppSourcePriv> findAllByAppSource(AppSource appSource);

    @Transactional
    Iterable<UUID> removeAllByAppSource(AppSource appSource);
}

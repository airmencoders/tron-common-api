package mil.tron.commonapi.repository.appsource;

import java.util.UUID;

import javax.transaction.Transactional;

import org.springframework.data.repository.CrudRepository;

import mil.tron.commonapi.entity.appsource.AppEndpointPriv;
import mil.tron.commonapi.entity.appsource.AppSource;

public interface AppEndpointPrivRepository extends CrudRepository<AppEndpointPriv, UUID> {
    Iterable<AppEndpointPriv> findAllByAppSource(AppSource appSource);

    @Transactional
    Iterable<UUID> removeAllByAppSource(AppSource appSource);
}

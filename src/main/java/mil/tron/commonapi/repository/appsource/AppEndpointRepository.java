package mil.tron.commonapi.repository.appsource;

import java.util.UUID;

import javax.transaction.Transactional;

import org.springframework.data.repository.CrudRepository;

import mil.tron.commonapi.entity.appsource.AppEndpoint;
import mil.tron.commonapi.entity.appsource.AppSource;

public interface AppEndpointRepository extends CrudRepository<AppEndpoint, UUID>{
    Iterable<AppEndpoint> findAllByAppSource(AppSource appSource);
    
    @Transactional
    Iterable<UUID> removeAllByAppSource(AppSource appSource);
}

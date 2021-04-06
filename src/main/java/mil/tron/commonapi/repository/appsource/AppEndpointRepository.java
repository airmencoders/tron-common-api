package mil.tron.commonapi.repository.appsource;

import java.util.UUID;

import javax.transaction.Transactional;

import org.springframework.data.repository.CrudRepository;

import mil.tron.commonapi.entity.appsource.AppEndpoint;
import mil.tron.commonapi.entity.appsource.AppSource;
import org.springframework.web.bind.annotation.RequestMethod;

public interface AppEndpointRepository extends CrudRepository<AppEndpoint, UUID>{
    Iterable<AppEndpoint> findAllByAppSource(AppSource appSource);
    Iterable<AppEndpoint> findAllByAppSourceEqualsAndMethodEqualsAndPathEquals(AppSource source, RequestMethod method, String path);
    boolean existsByAppSourceEqualsAndMethodEqualsAndAndPathEquals(AppSource appSource, RequestMethod method, String path);
    
    @Transactional
    Iterable<UUID> removeAllByAppSource(AppSource appSource);
}

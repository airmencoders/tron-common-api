package mil.tron.commonapi.repository.appsource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.transaction.Transactional;

import org.springframework.data.repository.CrudRepository;

import mil.tron.commonapi.entity.appsource.AppEndpoint;
import mil.tron.commonapi.entity.appsource.AppSource;
import org.springframework.web.bind.annotation.RequestMethod;

public interface AppEndpointRepository extends CrudRepository<AppEndpoint, UUID>{
    List<AppEndpoint> findAllByAppSource(AppSource appSource);
    Iterable<AppEndpoint> findAllByAppSourceEqualsAndMethodEqualsAndPathEquals(AppSource source, RequestMethod method, String path);
    AppEndpoint findByPathAndAppSourceAndMethod(String path, AppSource appSource, RequestMethod method);
    boolean existsByAppSourceEqualsAndMethodEqualsAndAndPathEquals(AppSource appSource, RequestMethod method, String path);
    Optional<AppEndpoint> findByAppSourceEqualsAndMethodEqualsAndPathEquals(AppSource appSource, RequestMethod method, String path);
    
    @Transactional
    Iterable<UUID> removeAllByAppSource(AppSource appSource);
}

package mil.tron.commonapi.repository.appsource;

import java.util.UUID;

import org.springframework.data.repository.CrudRepository;

import mil.tron.commonapi.entity.appsource.AppEndpoint;

public interface AppEndpointRepository extends CrudRepository<AppEndpoint, UUID>{
    
}

package mil.tron.commonapi.repository.appsource;

import mil.tron.commonapi.entity.appsource.AppSourcePriv;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface AppSourcePrivRepository extends CrudRepository<AppSourcePriv, UUID> {
    public Iterable<AppSourcePriv> findByAppSourceId(UUID appSourceId);
}

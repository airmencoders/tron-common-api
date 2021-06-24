package mil.tron.commonapi.repository.appsource;

import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.appsource.AppEndpoint;
import mil.tron.commonapi.entity.appsource.AppEndpointPriv;
import mil.tron.commonapi.entity.appsource.AppSource;
import org.springframework.data.jpa.repository.JpaRepository;

import javax.transaction.Transactional;
import java.util.UUID;

public interface AppEndpointPrivRepository extends JpaRepository<AppEndpointPriv, UUID> {
    Iterable<AppEndpointPriv> findAllByAppSource(AppSource appSource);
    boolean existsByAppSourceEqualsAndAppClientUserEqualsAndAppEndpointEquals(AppSource appSource,
                                                                              AppClientUser clientUser,
                                                                              AppEndpoint appEndpoint);
    boolean existsByAppSourceEqualsAndAppClientUserEquals(AppSource appSource, AppClientUser clientUser);

    @Transactional
    void removeAllByAppEndpoint(AppEndpoint appEndpoint);

    @Transactional
    Iterable<UUID> removeAllByAppSource(AppSource appSource);
}

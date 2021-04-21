package mil.tron.commonapi.repository;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import mil.tron.commonapi.entity.CountMetric;
import mil.tron.commonapi.entity.EndpointCountMetric;
import mil.tron.commonapi.entity.MeterValue;

public interface MeterValueRepository extends CrudRepository<MeterValue, UUID>{
    List<MeterValue> findAllByAppEndpointIdAndTimestampBetweenOrderByTimestampDesc(UUID id, Date startDate, Date endDate);
    
    @Query("SELECT c.appEndpoint.id AS id, c.appEndpoint.path AS name, SUM(c.value) AS sum, c.appEndpoint.method as method "
        + "FROM MeterValue AS c WHERE c.appSource.id = ?1 AND c.timestamp >= ?2 AND c.timestamp <= ?3 "
        + "GROUP BY c.appEndpoint.id, c.appEndpoint.path, c.appEndpoint.method ORDER BY c.appEndpoint.path ASC")
    List<EndpointCountMetric> sumByEndpoint(UUID appSource, Date startDate, Date endDate);
            
    @Query("SELECT c.appClientUser.id AS id, c.appClientUser.name AS name, SUM(c.value) AS sum "
    + "FROM MeterValue AS c WHERE c.appSource.id = ?1 AND c.appEndpoint.path = ?2 AND c.timestamp >= ?3 AND c.timestamp <= ?4 "
    + "GROUP BY c.appClientUser.id, c.appClientUser.name ORDER BY c.appClientUser.name ASC")
    List<CountMetric> sumByAppSourceAndAppClientForEndpoint(UUID appSource, String path, Date startDate, Date endDate);

    @Query("SELECT c.appClientUser.id AS id, c.appClientUser.name AS name, SUM(c.value) AS sum "
        + "FROM MeterValue AS c WHERE c.appSource.id = ?1 AND c.timestamp >= ?2 AND c.timestamp <= ?3 "
        + "GROUP BY c.appClientUser.id, c.appClientUser.name ORDER BY c.appClientUser.name ASC")
    List<CountMetric> sumByAppClient(UUID appSource, Date startDate, Date endDate);

    @Query("SELECT c.appEndpoint.id AS id, c.appEndpoint.path AS name, SUM(c.value) AS sum, c.appEndpoint.method as method "
        + "FROM MeterValue AS c WHERE c.appSource.id = ?1 AND UPPER(c.appClientUser.name) = UPPER(?2) AND c.timestamp >= ?3 AND c.timestamp <= ?4 "
        + "GROUP BY c.appEndpoint.id, c.appEndpoint.path, c.appEndpoint.method ORDER BY c.appEndpoint.path ASC")
    List<EndpointCountMetric> sumByAppSourceAndEndpointForAppClient(UUID appSource, String name, Date startDate, Date endDate);
}


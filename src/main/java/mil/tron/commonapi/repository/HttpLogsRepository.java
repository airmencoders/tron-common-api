package mil.tron.commonapi.repository;

import mil.tron.commonapi.dto.kpi.ServiceMetricDto;
import mil.tron.commonapi.entity.HttpLogEntry;
import mil.tron.commonapi.entity.dashboard.EntityAccessor;
import mil.tron.commonapi.entity.kpi.UserWithRequestCount;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HttpLogsRepository extends JpaRepository<HttpLogEntry, UUID> {

    @Query("select h from HttpLogEntry h where h.requestTimestamp >= :requestTimeStamp and " +
            "lower(h.requestMethod) like lower(:requestMethod) and lower(h.userName) like lower(:userName) and " +
            "(h.statusCode = :status or :status = -1) and " +
            "lower(h.userAgent) like lower(:userAgent) and lower(h.requestedUrl) like lower(:requestedUrl) order by h.requestTimestamp")
    Page<HttpLogEntry> findRequestedLogs
            (Date requestTimeStamp, String requestMethod, String userName, int status, String userAgent, String requestedUrl, Pageable page);
    
    @Query(value = "SELECT h.userName AS name, COUNT(*) AS requestCount"
    		+ " FROM HttpLogEntry h"
    		+ " WHERE h.requestTimestamp BETWEEN :startDate and :endDate"
    		+ " GROUP BY h.userName")
    List<UserWithRequestCount> getUsersWithRequestCount(Date startDate, Date endDate);
    
    @Query(value = "SELECT AVG(h.timeTakenMs)"
    		+ " FROM HttpLogEntry h"
    		+ " WHERE h.statusCode >= 200 AND h.statusCode < 300 AND h.requestTimestamp BETWEEN :startDate and :endDate")
    Optional<Double> getAverageLatencyForSuccessfulResponse(Date startDate, Date endDate);
    
    @Query(value = "SELECT new mil.tron.commonapi.dto.kpi.ServiceMetricDto("
    		+ " CASE"
    		+ " WHEN h.requestedUrl LIKE CONCAT('%/api/v%', :appGatewayPrefix, '/%') THEN substring(substring(h.requestedUrl, locate(:appGatewayPrefix, h.requestedUrl) + 5), 1, locate('/', substring(h.requestedUrl, locate(:appGatewayPrefix, h.requestedUrl) + 5)) - 1)"
    		+ " WHEN h.requestedUrl LIKE '%/api/v%/organization%' THEN 'Organization'"
    		+ " WHEN h.requestedUrl LIKE '%/api/v%/person%' THEN 'Person'"
    		+ " ELSE 'Other'"
    		+ " END as name, AVG(h.timeTakenMs) as averageLatency, COUNT(*) as responseCount)"
    		+ " FROM HttpLogEntry h"
    		+ " WHERE h.statusCode >= 200 AND h.statusCode < 300 AND h.requestTimestamp BETWEEN :startDate and :endDate"
    		+ " GROUP BY name")
    List<ServiceMetricDto> getMetricsForSuccessfulResponsesByService(Date startDate, Date endDate, String appGatewayPrefix);
    
    /**
     * Gets all Users (includes app clients and dashboard users) that 
     * have made a request like '%/api%/organization%'.
     * Will only retrieve Users that have made requests between {@code startDate} and 
     * {@code endDate} with an http status code between 200 and 300.
     * 
     * @param startDate date to start search from
     * @param endDate date to end search at
     * @return a list of user names
     */
    @Query(value = "SELECT h.userName as name, COUNT(*) as recordAccessCount"
   		+ " FROM"
   		+ " HttpLogEntry h"
   		+ " WHERE"
   		+ " h.requestedUrl LIKE '%/api%/organization%' AND h.requestedUrl NOT LIKE '%/api/v%/app/%'"
   		+ " AND h.requestTimestamp BETWEEN :startDate and :endDate"
   		+ " AND h.statusCode BETWEEN 200 and 299"
   		+ " GROUP BY h.userName")
    List<EntityAccessor> getUsersAccessingOrgRecords(Date startDate, Date endDate);
    
    @Query(value = "SELECT h"
       		+ " FROM"
       		+ " HttpLogEntry h"
       		+ " WHERE"
       		+ " h.requestedUrl LIKE '%/api%/app/%'"
       		+ " AND h.requestTimestamp BETWEEN :startDate and :endDate"
       		+ " AND h.statusCode BETWEEN 200 and 299")
    List<HttpLogEntry> getAppSourceUsage(Date startDate, Date endDate);
    
    @Query(value = "SELECT h"
       		+ " FROM"
       		+ " HttpLogEntry h"
       		+ " WHERE"
       		+ " h.requestedUrl LIKE '%/api%/app/%'"
       		+ " AND h.requestTimestamp BETWEEN :startDate and :endDate"
       		+ " AND h.statusCode BETWEEN 400 and 599")
    List<HttpLogEntry> getAppSourceErrorUsage(Date startDate, Date endDate);
}

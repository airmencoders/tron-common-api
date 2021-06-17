package mil.tron.commonapi.repository;

import mil.tron.commonapi.entity.HttpLogEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.UUID;

@Repository
public interface HttpLogsRepository extends JpaRepository<HttpLogEntry, UUID> {

    @Query("select h from HttpLogEntry h where h.requestTimestamp >= :requestTimeStamp and " +
            "lower(h.requestMethod) like lower(:requestMethod) and lower(h.userName) like lower(:userName) and " +
            "(h.statusCode = :status or :status = -1) and " +
            "lower(h.userAgent) like lower(:userAgent) and lower(h.requestedUrl) like lower(:requestedUrl) order by h.requestTimestamp")
    Page<HttpLogEntry> findRequestedLogs
            (Date requestTimeStamp, String requestMethod, String userName, int status, String userAgent, String requestedUrl, Pageable page);
}

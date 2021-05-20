package mil.tron.commonapi.repository;

import mil.tron.commonapi.entity.HttpLogEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.UUID;

@Repository
public interface HttpLogsRepository extends JpaRepository<HttpLogEntry, UUID> {

    Slice<HttpLogEntry> findByRequestTimestampGreaterThanEqual(Date requestTimestamp, Pageable page);
}

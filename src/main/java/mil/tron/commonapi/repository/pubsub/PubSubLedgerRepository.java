package mil.tron.commonapi.repository.pubsub;

import mil.tron.commonapi.entity.pubsub.PubSubLedger;
import mil.tron.commonapi.entity.pubsub.events.EventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PubSubLedgerRepository extends JpaRepository<PubSubLedger, UUID> {
    Long countByEventType(EventType type);
    Iterable<PubSubLedger> findByDateCreatedGreaterThan(Date date);
    Iterable<PubSubLedger> findByDateCreatedGreaterThanEqual(Date date);
    Optional<PubSubLedger> findByEventTypeEqualsAndCountForEventTypeEquals(EventType eventType, Long eventCount);
}

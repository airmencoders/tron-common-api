package mil.tron.commonapi.repository.pubsub;

import mil.tron.commonapi.entity.pubsub.PubSubLedger;
import mil.tron.commonapi.entity.pubsub.events.EventType;
import org.springframework.data.repository.CrudRepository;

import java.util.Date;
import java.util.UUID;

public interface PubSubLedgerRepository extends CrudRepository<PubSubLedger, UUID> {
    public Long countByEventType(EventType type);
    public Iterable<PubSubLedger> findByDateCreatedGreaterThan(Date date);
}

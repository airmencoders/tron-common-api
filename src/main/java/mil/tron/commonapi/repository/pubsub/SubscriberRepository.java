package mil.tron.commonapi.repository.pubsub;

import mil.tron.commonapi.entity.pubsub.events.EventTypes;
import mil.tron.commonapi.entity.pubsub.Subscriber;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubscriberRepository extends CrudRepository<Subscriber, UUID> {

    Iterable<Subscriber> findAllBySubscribedEvent(EventTypes event);
    Optional<Subscriber> findBySubscriberAddressAndSubscribedEvent(String address, EventTypes event);
}

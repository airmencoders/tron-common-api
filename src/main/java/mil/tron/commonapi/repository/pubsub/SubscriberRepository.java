package mil.tron.commonapi.repository.pubsub;

import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.pubsub.Subscriber;
import mil.tron.commonapi.entity.pubsub.events.EventType;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface SubscriberRepository extends CrudRepository<Subscriber, UUID> {

    Iterable<Subscriber> findAllBySubscribedEvent(EventType event);
    Iterable<Subscriber> findByAppClientUser(AppClientUser appClientUser);
}

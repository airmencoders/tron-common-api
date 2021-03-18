package mil.tron.commonapi.service.pubsub;

import mil.tron.commonapi.entity.pubsub.Subscriber;
import mil.tron.commonapi.entity.pubsub.events.EventType;

import java.util.UUID;

public interface SubscriberService {

    Iterable<Subscriber> getAllSubscriptions();
    Subscriber getSubscriberById(UUID id);
    Subscriber upsertSubscription(Subscriber subscriber);
    void cancelSubscription(UUID id);

    Iterable<Subscriber> getSubscriptionsByEventType(EventType type);

}

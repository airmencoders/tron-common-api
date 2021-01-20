package mil.tron.commonapi.service.pubsub;

import mil.tron.commonapi.entity.pubsub.Subscriber;
import mil.tron.commonapi.entity.pubsub.events.EventTypes;

import java.util.UUID;

public interface SubscriberService {

    Iterable<Subscriber> getAllSubscriptions();
    Subscriber getSubscriberById(UUID id);
    Subscriber createSubscription(Subscriber subscriber);
    Subscriber updateSubscription(UUID id, Subscriber subscriber);
    void cancelSubscription(UUID id);

    Iterable<Subscriber> getSubscriptionsByEventType(EventTypes type);

}

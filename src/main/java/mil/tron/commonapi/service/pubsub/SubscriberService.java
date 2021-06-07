package mil.tron.commonapi.service.pubsub;

import mil.tron.commonapi.dto.pubsub.SubscriberDto;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.pubsub.Subscriber;
import mil.tron.commonapi.entity.pubsub.events.EventType;

import java.util.UUID;

public interface SubscriberService {

    Iterable<SubscriberDto> getAllSubscriptions();
    SubscriberDto getSubscriberById(UUID id);
    boolean subscriptionExists(UUID id);
    SubscriberDto upsertSubscription(SubscriberDto subscriber);
    void cancelSubscription(UUID id);
    void cancelSubscriptionsByAppClient(AppClientUser appClientUser);

    Iterable<Subscriber> getSubscriptionsByEventType(EventType type);

}

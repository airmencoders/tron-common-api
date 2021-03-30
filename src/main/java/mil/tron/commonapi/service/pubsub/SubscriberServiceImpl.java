package mil.tron.commonapi.service.pubsub;

import mil.tron.commonapi.entity.pubsub.Subscriber;
import mil.tron.commonapi.entity.pubsub.events.EventType;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.pubsub.SubscriberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Backs the Subscriber Controller and provides the logic for adding/managing subscriptions
 */
@Service
public class SubscriberServiceImpl implements SubscriberService {

    @Autowired
    SubscriberRepository subRepo;

    @Override
    public Iterable<Subscriber> getAllSubscriptions() {
        return subRepo.findAll();
    }

    @Override
    public Subscriber getSubscriberById(UUID id) {
        return subRepo.findById(id).orElseThrow(() ->
                new RecordNotFoundException("Subscription with resource ID: " + id.toString() + " does not exist."));
    }

    @Override
    public Subscriber upsertSubscription(Subscriber subscriber) {
        if (subscriber.getId() == null) {
            subscriber.setId(UUID.randomUUID());
        }

        Subscriber existing = subRepo.findBySubscriberAddressAndSubscribedEvent(subscriber.getSubscriberAddress(), subscriber.getSubscribedEvent()).orElseGet(() -> subscriber);
        existing.setSecret(subscriber.getSecret());

        return subRepo.save(existing);
    }

    @Override
    public void cancelSubscription(UUID id) {
        if (subRepo.existsById(id)) {
            subRepo.deleteById(id);
        }
        else {
            throw new RecordNotFoundException("Subscription with UUID: " + id.toString() + " does not exist");
        }
    }

    @Override
    public Iterable<Subscriber> getSubscriptionsByEventType(EventType type) {
        return subRepo.findAllBySubscribedEvent(type);
    }
}

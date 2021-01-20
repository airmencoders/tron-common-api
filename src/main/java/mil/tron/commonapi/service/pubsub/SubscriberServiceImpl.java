package mil.tron.commonapi.service.pubsub;

import mil.tron.commonapi.entity.pubsub.Subscriber;
import mil.tron.commonapi.entity.pubsub.events.EventTypes;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
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
    public Subscriber createSubscription(Subscriber subscriber) {
        if (subscriber.getId() == null) {
            subscriber.setId(UUID.randomUUID());
        }

         // Ensure no duplicate subscriptions exist
         subRepo.findBySubscriberAddressAndSubscribedEvent(subscriber.getSubscriberAddress(), subscriber.getSubscribedEvent()).ifPresent(item -> {
            throw new ResourceAlreadyExistsException("Subscription already exists for that address and event type under ID: " + item.getId());
        });

        if (!subRepo.existsById(subscriber.getId())) {
            return subRepo.save(subscriber);
        }

        throw new ResourceAlreadyExistsException("Subscription already exists by ID: " + subscriber.getId().toString());
    }

    @Override
    public Subscriber updateSubscription(UUID id, Subscriber subscriber) {
        if(!subRepo.existsById(id)) {
            throw new RecordNotFoundException("Provided subscription UUID: " + id.toString() + " does not match any existing records");
        }

        // Ensure no duplicate subscriptions exist
        subRepo.findBySubscriberAddressAndSubscribedEvent(subscriber.getSubscriberAddress(), subscriber.getSubscribedEvent()).ifPresent(item -> {
            throw new ResourceAlreadyExistsException("Subscription already exists for that address and event type under ID: " + item.getId());
        });

        // the subscription object's id better match the id given,
        //  otherwise hibernate will save under whatever id's inside the object
        if (!subscriber.getId().equals(id)) {
            throw new InvalidRecordUpdateRequest(
                    "Provided subscription UUID " + subscriber.getId() + " mismatched UUID in subscription object");
        }

        return subRepo.save(subscriber);
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
    public Iterable<Subscriber> getSubscriptionsByEventType(EventTypes type) {
        return subRepo.findAllBySubscribedEvent(type);
    }
}

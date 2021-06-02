package mil.tron.commonapi.service.pubsub;

import mil.tron.commonapi.dto.pubsub.SubscriberDto;
import mil.tron.commonapi.entity.pubsub.Subscriber;
import mil.tron.commonapi.entity.pubsub.events.EventType;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.pubsub.SubscriberRepository;
import org.assertj.core.util.Lists;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Backs the Subscriber Controller and provides the logic for adding/managing subscriptions
 */
@Service
public class SubscriberServiceImpl implements SubscriberService {

    @Autowired
    SubscriberRepository subscriberRepository;

    private ModelMapper mapper = new ModelMapper();

    @Override
    public Iterable<SubscriberDto> getAllSubscriptions() {
        return Lists.newArrayList(subscriberRepository.findAll())
                .stream()
                .map(item -> mapper.map(item, SubscriberDto.class))
                .collect(Collectors.toList());
    }

    @Override
    public SubscriberDto getSubscriberById(UUID id) {
        return mapper.map(subscriberRepository
                .findById(id)
                .orElseThrow(() ->
                    new RecordNotFoundException("Subscription with resource ID: " + id.toString() + " does not exist."))
                , SubscriberDto.class);
    }

    @Override
    public boolean subscriptionExists(UUID id) {
        return subscriberRepository.existsById(id);
    }

    @Override
    public SubscriberDto upsertSubscription(SubscriberDto subscriber) {
        if (subscriber.getId() == null) {
            subscriber.setId(UUID.randomUUID());
        }

        Subscriber existing = subscriberRepository
                .findBySubscriberAddressAndSubscribedEvent(subscriber.getSubscriberAddress(),
                        subscriber.getSubscribedEvent())
                .orElseGet(() -> mapper.map(subscriber, Subscriber.class));

        existing.setSecret(subscriber.getSecret());

        return mapper.map(subscriberRepository.save(existing), SubscriberDto.class);
    }

    @Override
    public void cancelSubscription(UUID id) {
        if (subscriberRepository.existsById(id)) {
            subscriberRepository.deleteById(id);
        }
        else {
            throw new RecordNotFoundException("Subscription with UUID: " + id.toString() + " does not exist");
        }
    }

    @Override
    public Iterable<Subscriber> getSubscriptionsByEventType(EventType type) {
        return subscriberRepository.findAllBySubscribedEvent(type);
    }
}

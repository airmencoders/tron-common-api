package mil.tron.commonapi.service.pubsub;

import mil.tron.commonapi.dto.pubsub.SubscriberDto;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.pubsub.Subscriber;
import mil.tron.commonapi.entity.pubsub.events.EventType;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.pubsub.SubscriberRepository;
import org.assertj.core.util.Lists;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Backs the Subscriber Controller and provides the logic for adding/managing subscriptions
 */
@Service
public class SubscriberServiceImpl implements SubscriberService {

    private SubscriberRepository subscriberRepository;
    private AppClientUserRespository appClientUserRespository;

    public SubscriberServiceImpl(SubscriberRepository subscriberRepository,
                                 AppClientUserRespository appClientUserRespository) {
        this.subscriberRepository = subscriberRepository;
        this.appClientUserRespository = appClientUserRespository;
    }

    private ModelMapper mapper = new ModelMapper();
    private final static String APP_CLIENT_NOT_FOUND_ERR = "App Client %s not found";

    @Override
    public Iterable<SubscriberDto> getAllSubscriptions() {
        return Lists.newArrayList(subscriberRepository.findAll())
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    public SubscriberDto getSubscriberById(UUID id) {
        return mapToDto(subscriberRepository
                .findById(id)
                .orElseThrow(() ->
                    new RecordNotFoundException("Subscription with resource ID: " + id.toString() + " does not exist.")));
    }

    @Override
    public boolean subscriptionExists(UUID id) {
        return subscriberRepository.existsById(id);
    }

    private SubscriberDto mapToDto(Subscriber subscriber) {
        SubscriberDto dto = mapper.map(subscriber, SubscriberDto.class);
        if (subscriber.getAppClientUser() != null) {
            dto.setAppClientUser(subscriber.getAppClientUser().getName());
        }
        return dto;
    }

    @Override
    public SubscriberDto upsertSubscription(SubscriberDto subscriber) {
        if (subscriber.getId() == null) {
            subscriber.setId(UUID.randomUUID());
        }

        System.out.println("EVENT: " + subscriber.getSubscribedEvent().toString());

        if (subscriber.getAppClientUser() == null)
            throw new BadRequestException("App Client cannot be null");

        // get the requested app client for this subscription, so as to validate it exists
        AppClientUser appClientUser = appClientUserRespository
                .findByNameIgnoreCase(subscriber.getAppClientUser())
                .orElseThrow(() -> new RecordNotFoundException(String.format(APP_CLIENT_NOT_FOUND_ERR, subscriber.getAppClientUser())));

        System.out.println(subscriber.getAppClientUser() + " " + subscriber.getSubscribedEvent());
        System.out.println(appClientUser.getName());
        // try to get existing...
        Optional<Subscriber> existing = subscriberRepository
                .findByAppClientUserAndSubscribedEvent(appClientUser, subscriber.getSubscribedEvent());

        if (existing.isPresent()) {
            // edit an existing
            System.out.println("HERE!");
            Subscriber sub = existing.get();
            sub.setSecret(sub.getSecret());  // dont allow change secret on an update,
                                                // need to recreate a subscription if needed changed
            sub.setSubscribedEvent(subscriber.getSubscribedEvent());
            sub.setSubscriberAddress(subscriber.getSubscriberAddress());
            sub.setAppClientUser(appClientUser);
            return mapToDto(subscriberRepository.save(sub));

        } else {
            // make new subscription
            Subscriber sub = subscriberRepository.save(Subscriber.builder()
                    .secret(subscriber.getSecret())
                    .subscribedEvent(subscriber.getSubscribedEvent())
                    .subscriberAddress(subscriber.getSubscriberAddress())
                    .appClientUser(appClientUser)
                    .build());

            return mapToDto(sub);
        }
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

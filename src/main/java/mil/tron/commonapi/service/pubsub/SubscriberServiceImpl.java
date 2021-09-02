package mil.tron.commonapi.service.pubsub;

import mil.tron.commonapi.dto.pubsub.SubscriberDto;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.pubsub.Subscriber;
import mil.tron.commonapi.entity.pubsub.events.EventType;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.exception.InvalidAppSourcePermissions;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.pubsub.SubscriberRepository;
import org.assertj.core.util.Lists;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;
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

        if (subscriber.getAppClientUser() == null)
            throw new BadRequestException("App Client cannot be null");

        // get the requested app client for this subscription, so as to validate it exists
        AppClientUser appClientUser = appClientUserRespository
                .findByNameIgnoreCase(subscriber.getAppClientUser())
                .orElseThrow(() -> new RecordNotFoundException(String.format(APP_CLIENT_NOT_FOUND_ERR, subscriber.getAppClientUser())));

        // try to get existing...
        Optional<Subscriber> existing = subscriberRepository.findById(subscriber.getId());

        if (existing.isPresent()) {

            // check no existing subs for this event type
            //checkNoDuplicateSubscriptionForApp(subscriber.getSubscribedEvent(), appClientUser);

            // edit an existing
            Subscriber sub = existing.get();
            sub.setSecret(sub.getSecret());  // dont allow change secret on an update,
                                                // need to recreate a subscription if needed changed
            sub.setSubscribedEvent(subscriber.getSubscribedEvent());
            sub.setSubscriberAddress(subscriber.getSubscriberAddress());
            sub.setAppClientUser(appClientUser);
            checkAppHasReadAccessToEntity(mapToDto(sub), appClientUser);
            return mapToDto(subscriberRepository.save(sub));

        } else {
            checkAppHasReadAccessToEntity(subscriber, appClientUser);

            // check no existing subs for this event type
            checkNoDuplicateSubscriptionForApp(subscriber.getSubscribedEvent(), appClientUser);
            Subscriber sub = subscriberRepository.save(Subscriber.builder()
                    .secret(subscriber.getSecret())
                    .subscribedEvent(subscriber.getSubscribedEvent())
                    .subscriberAddress(subscriber.getSubscriberAddress())
                    .appClientUser(appClientUser)
                    .build());

            return mapToDto(sub);
        }
    }

    /**
     * Helper to check if a given app client has READ privs to the entity type they're trying to subscribe to
     * @param subscriber
     * @param appClientUser
     */
    private void checkAppHasReadAccessToEntity(SubscriberDto subscriber, AppClientUser appClientUser) {
        // make new subscription, but need to make sure requester has READ access to target entity
        String entityType = SubscriberServiceImpl.getTargetEntityType(subscriber.getSubscribedEvent());
        if (!appClientUser
                .getPrivileges()
                .stream()
                .map(Privilege::getName)
                .collect(Collectors.toSet())
                .contains(entityType + "_READ")) {

            throw new InvalidAppSourcePermissions("Cannot subscribe to entity " + entityType + " without READ access");
        }
    }

    /**
     * Helper to check that a proposed create/edit won't result in a duplicate subscription for a given appclient
     * @param proposedEvent
     * @param appClientUser
     */
    private void checkNoDuplicateSubscriptionForApp(EventType proposedEvent, AppClientUser appClientUser) {
        List<Subscriber> subs = Lists.newArrayList(subscriberRepository.findByAppClientUser(appClientUser));
        if (subs.stream()
                .map(Subscriber::getSubscribedEvent)
                .collect(Collectors.toList())
                .contains(proposedEvent)) {
            throw new ResourceAlreadyExistsException("Subscription already exists for this app client for this event");
        }
    }

    /**
     * Helper to return string representation of the Event's target entity,
     * we can easily get this from the prefix of the event name
     * @param event
     * @return
     */
    public static String getTargetEntityType(EventType event) {
        String type = event.toString().split("_")[0];

        // account for "SUB_ORG_*" which are really ORGANIZATION type
        if (type.equals("SUB")) return "ORGANIZATION";
        else return type;
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

    /**
     * Deletes all subscriptions for a given app client (if there were any)
     * @param appClientUser the app client from which to delete all subscriptions
     */
    @Override
    public void cancelSubscriptionsByAppClient(AppClientUser appClientUser) {
        List<Subscriber> subs = Lists.newArrayList(subscriberRepository.findByAppClientUser(appClientUser));

        for (Subscriber s : subs) {
            subscriberRepository.delete(s);
        }
    }

    @Override
    public Iterable<Subscriber> getSubscriptionsByEventType(EventType type) {
        return subscriberRepository.findAllBySubscribedEvent(type);
    }
}

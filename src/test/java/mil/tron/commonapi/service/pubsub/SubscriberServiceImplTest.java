package mil.tron.commonapi.service.pubsub;

import mil.tron.commonapi.entity.pubsub.Subscriber;
import mil.tron.commonapi.entity.pubsub.events.EventType;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.exception.ResourceAlreadyExistsException;
import mil.tron.commonapi.repository.pubsub.SubscriberRepository;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class SubscriberServiceImplTest {

    @InjectMocks
    SubscriberServiceImpl subscriberService;

    @Mock
    SubscriberRepository subscriberRepository;

    private Subscriber subscriber;


    @BeforeEach
    void setup() {
        subscriber = Subscriber.builder()
                .id(UUID.randomUUID())
                .subscriberAddress("some.address")
                .subscribedEvent(EventType.PERSON_CHANGE)
                .build();


    }

    @Test
    void testGetAllSubsriptions() {
        Mockito.when(subscriberRepository.findAll()).thenReturn(Lists.newArrayList(subscriber));

        assertEquals(1, Lists.newArrayList(subscriberService.getAllSubscriptions()).size());
    }

    @Test
    void testGetSubscription() {
        Mockito.when(subscriberRepository.findById(subscriber.getId())).thenReturn(Optional.of(subscriber));
        assertEquals(subscriber.getId(), subscriberService.getSubscriberById(subscriber.getId()).getId());

        Mockito.when(subscriberRepository.findById(subscriber.getId())).thenThrow(new RecordNotFoundException("Not found"));
        assertThrows(RecordNotFoundException.class, () -> subscriberService.getSubscriberById(subscriber.getId()));
    }

    @Test
    void testCreateSubscription() {
        Mockito.when(subscriberRepository
                .findBySubscriberAddressAndSubscribedEvent(Mockito.any(String.class), Mockito.any(EventType.class)))
                .thenReturn(Optional.empty());

        Mockito.when(subscriberRepository.existsById(Mockito.any(UUID.class)))
                .thenReturn(false);

        Mockito.when(subscriberRepository.save(Mockito.any(Subscriber.class)))
                .thenReturn(subscriber);

        assertEquals(subscriber.getId(), subscriberService.createSubscription(subscriber).getId());

        Mockito.when(subscriberRepository
                .findBySubscriberAddressAndSubscribedEvent(Mockito.any(String.class), Mockito.any(EventType.class)))
                .thenReturn(Optional.of(subscriber));

        assertThrows(ResourceAlreadyExistsException.class, () -> subscriberService.createSubscription(subscriber));

        Mockito.when(subscriberRepository
                .findBySubscriberAddressAndSubscribedEvent(Mockito.any(String.class), Mockito.any(EventType.class)))
                .thenReturn(Optional.empty());

        Mockito.when(subscriberRepository.existsById(Mockito.any(UUID.class)))
                .thenReturn(true);

        assertThrows(ResourceAlreadyExistsException.class, () -> subscriberService.createSubscription(subscriber));
    }

    @Test
    void testUpdateSubscription() {
        Mockito.when(subscriberRepository.existsById(Mockito.any(UUID.class))).thenReturn(false);
        assertThrows(RecordNotFoundException.class, () -> subscriberService.updateSubscription(subscriber.getId(), subscriber));

        Mockito.when(subscriberRepository.existsById(Mockito.any(UUID.class))).thenReturn(true);
        Mockito.when(subscriberRepository
                .findBySubscriberAddressAndSubscribedEvent(Mockito.any(String.class), Mockito.any(EventType.class)))
                .thenReturn(Optional.of(subscriber));
        assertThrows(ResourceAlreadyExistsException.class, () -> subscriberService.updateSubscription(subscriber.getId(), subscriber));

        Mockito.when(subscriberRepository
                .findBySubscriberAddressAndSubscribedEvent(Mockito.any(String.class), Mockito.any(EventType.class)))
                .thenReturn(Optional.empty());

        assertThrows(InvalidRecordUpdateRequest.class, () -> subscriberService.updateSubscription(UUID.randomUUID(), subscriber));

        Mockito.when(subscriberRepository.save(Mockito.any(Subscriber.class))).thenReturn(subscriber);
        assertEquals(subscriber.getId(), subscriberService.updateSubscription(subscriber.getId(), subscriber).getId());
    }

    @Test
    void testCancelSubscription() {
        Mockito.when(subscriberRepository.existsById(Mockito.any(UUID.class)))
                .thenReturn(true)
                .thenReturn(false);

        assertDoesNotThrow(() -> subscriberService.cancelSubscription(subscriber.getId()));
        assertThrows(RecordNotFoundException.class, () -> subscriberService.cancelSubscription(subscriber.getId()));
    }

    @Test
    void testGetSubscriptionsByEventType() {
        Mockito.when(subscriberRepository.findAllBySubscribedEvent(Mockito.any(EventType.class)))
                .thenReturn(Lists.newArrayList(subscriber));

        assertEquals(1, Lists.newArrayList(subscriberService.getSubscriptionsByEventType(EventType.PERSON_CHANGE)).size());
    }

}

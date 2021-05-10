package mil.tron.commonapi.service.pubsub;

import mil.tron.commonapi.dto.pubsub.SubscriberDto;
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
import org.modelmapper.ModelMapper;

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
    private ModelMapper mapper = new ModelMapper();

    @BeforeEach
    void setup() {
        subscriber = Subscriber.builder()
                .id(UUID.randomUUID())
                .subscriberAddress("some.address")
                .subscribedEvent(EventType.PERSON_CHANGE)
                .secret("secret")
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
    void testUpsertSubscriptionNew() {
        Mockito.when(subscriberRepository
                .findBySubscriberAddressAndSubscribedEvent(Mockito.any(String.class), Mockito.any(EventType.class)))
                .thenReturn(Optional.empty());

        Mockito.when(subscriberRepository.save(Mockito.any(Subscriber.class)))
            .thenAnswer(s -> s.getArgument(0));

        var result = subscriberService.upsertSubscription(mapper.map(subscriber, SubscriberDto.class));
        assertEquals(subscriber.getSecret(), result.getSecret());
        assertEquals(subscriber.getSubscribedEvent(), result.getSubscribedEvent());
        assertEquals(subscriber.getSubscriberAddress(), result.getSubscriberAddress());
    }

    @Test
    void testUpsertSubscriptionExisting() {
        Mockito.when(subscriberRepository
                .findBySubscriberAddressAndSubscribedEvent(Mockito.any(String.class), Mockito.any(EventType.class)))
                .thenReturn(Optional.of(subscriber));

        Mockito.when(subscriberRepository.save(Mockito.any(Subscriber.class)))
            .thenAnswer(s -> s.getArgument(0));

        var result = subscriberService.upsertSubscription(SubscriberDto.builder()
            .subscribedEvent(subscriber.getSubscribedEvent())
            .subscriberAddress(subscriber.getSubscriberAddress())
            .secret("new secret")
            .build());
        
        assertEquals(subscriber.getId(), result.getId());
        assertEquals("new secret", result.getSecret());
        assertEquals(subscriber.getSubscribedEvent(), result.getSubscribedEvent());
        assertEquals(subscriber.getSubscriberAddress(), result.getSubscriberAddress());
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

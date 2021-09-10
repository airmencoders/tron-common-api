package mil.tron.commonapi.service.pubsub.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.google.common.collect.Lists;

import mil.tron.commonapi.dto.appclient.AppClientSummaryDto;
import mil.tron.commonapi.dto.pubsub.log.EventRequestLogDto;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.pubsub.Subscriber;
import mil.tron.commonapi.entity.pubsub.events.EventType;
import mil.tron.commonapi.entity.pubsub.log.EventRequestLog;
import mil.tron.commonapi.pubsub.messages.PubSubMessage;
import mil.tron.commonapi.repository.filter.FilterCondition;
import mil.tron.commonapi.repository.filter.FilterCriteria;
import mil.tron.commonapi.repository.filter.QueryOperator;
import mil.tron.commonapi.repository.pubsub.log.EventRequestLogRepository;

@ExtendWith(MockitoExtension.class)
class EventRequestServiceImplTest {
	@Mock
	private EventRequestLogRepository eventRequestLogRepo;
	
	@Mock
	private Clock systemUtcClock;
	
	@InjectMocks
	EventRequestServiceImpl eventRequestService;
	
	private AppClientUser appClientUser;
	private Subscriber subscriber;
	List<EventRequestLog> eventRequestLogEntries;
	
	private Page<EventRequestLog> pageResponse;
	private Pageable pageable;
	
	@BeforeEach
	void setup() {
		eventRequestLogEntries = new ArrayList<>();
		
		appClientUser = AppClientUser.builder()
				.availableAsAppClient(true)
				.id(UUID.randomUUID())
				.name("TEST APP CLIENT USER")
				.nameAsLower("TEST APP CLIENT USER".toLowerCase())
				.build();
		
		subscriber = Subscriber.builder()
				.appClientUser(appClientUser)
				.id(UUID.randomUUID())
				.secret("test")
				.subscribedEvent(EventType.ORGANIZATION_CHANGE)
				.subscriberAddress("http://localhost/test")
				.build();
		
		Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(1626825600000L), ZoneId.of("UTC"));
		Mockito.when(systemUtcClock.instant()).thenReturn(fixedClock.instant());
		
		eventRequestLogEntries.add(EventRequestLog.builder()
				.appClientUser(subscriber.getAppClientUser())
				.eventType(subscriber.getSubscribedEvent())
				.eventCount(1L)
				.lastAttempted(Date.from(Instant.now(systemUtcClock)))
				.wasSuccessful(true)
				.reason("Success")
				.build());
		
		pageable = PageRequest.of(0, 100);
		pageResponse = new PageImpl<>(eventRequestLogEntries, pageable, eventRequestLogEntries.size());
	}
	
	@Test
	void getAllPaged_shouldReturn_allEntries() {
		Mockito.when(eventRequestLogRepo.findAll(Mockito.any(Pageable.class))).thenReturn(pageResponse);
		
		assertThat(eventRequestService.getAllPaged(pageable)).containsAll(pageResponse.map(eventRequestService::convertToDto));
	}
	
	@Test
	void getAllPagedWithSpec_shouldReturn_allEntriesWithSpec() {
		Mockito.when(eventRequestLogRepo.findAll(Mockito.any(), Mockito.any(Pageable.class))).thenReturn(pageResponse);
		
		FilterCriteria criteria = FilterCriteria.builder()
				.field("wasSuccessful")
				.conditions(List.of(
						FilterCondition.builder()
							.operator(QueryOperator.EQUALS)
							.value("true")
							.build())
					)
				.build();
		
		assertThat(eventRequestService.getAllPagedWithSpec(pageable, Lists.newArrayList(criteria))).containsAll(pageResponse.map(eventRequestService::convertToDto));
	}
	
	@Test
	void getByAppIdPaged_shouldReturn_allEntriesWithId() {
		Mockito.when(eventRequestLogRepo.findAllByAppClientUser_Id(Mockito.any(Pageable.class), Mockito.any(UUID.class))).thenReturn(pageResponse);
		
		assertThat(eventRequestService.getByAppIdPaged(pageable, appClientUser.getId())).containsAll(pageResponse.map(eventRequestService::convertToDto));
	}
	
	@Test
	void getByAppIdPagedWithSpec_shouldReturn_allEntriesWithSpec() {
		Mockito.when(eventRequestLogRepo.findAll(Mockito.any(), Mockito.any(Pageable.class))).thenReturn(pageResponse);
		
		FilterCriteria criteria = FilterCriteria.builder()
				.field("wasSuccessful")
				.conditions(List.of(
						FilterCondition.builder()
							.operator(QueryOperator.EQUALS)
							.value("true")
							.build())
					)
				.build();
		
		assertThat(eventRequestService.getByAppIdPagedWithSpec(pageable, appClientUser.getId(), Lists.newArrayList(criteria)))
			.containsAll(pageResponse.map(eventRequestService::convertToDto));
	}
	
	@Test
	void getByAppNamePaged_shouldReturn_allEntries() {
		Mockito.when(eventRequestLogRepo.findAllByAppClientUser_NameAsLower(Mockito.any(Pageable.class), Mockito.anyString())).thenReturn(pageResponse);
		
		assertThat(eventRequestService.getByAppNamePaged(pageable, appClientUser.getNameAsLower()))
			.containsAll(pageResponse.map(eventRequestService::convertToDto));
	}
	
	@Test
	void getByAppNamePagedWithSpec_shouldReturn_allEntriesWithSpec() {
		Mockito.when(eventRequestLogRepo.findAll(Mockito.any(), Mockito.any(Pageable.class))).thenReturn(pageResponse);
		
		FilterCriteria criteria = FilterCriteria.builder()
				.field("wasSuccessful")
				.conditions(List.of(
						FilterCondition.builder()
							.operator(QueryOperator.EQUALS)
							.value("true")
							.build())
					)
				.build();
		
		assertThat(eventRequestService.getByAppNamePagedWithSpec(pageable, appClientUser.getName(), Lists.newArrayList(criteria)))
			.containsAll(pageResponse.map(eventRequestService::convertToDto));
	}
	
	@Test
	void createEventRequestLogEntry_shouldReturn_eventRequestLog() {
		PubSubMessage pubSubMessage = PubSubMessage.builder()
				.eventCount(1L)
				.eventType(EventType.ORGANIZATION_CHANGE)
				.build();
		
		Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(1626825600000L), ZoneId.of("UTC"));
		Mockito.when(systemUtcClock.instant()).thenReturn(fixedClock.instant());
		
		EventRequestLog createdEntry = eventRequestService.createEventRequestLogEntry(subscriber, pubSubMessage, true, "Success");
		
		assertThat(createdEntry)
			.usingRecursiveComparison()
			.ignoringFields("id")
			.isEqualTo(EventRequestLog.builder()
					.appClientUser(appClientUser)
					.eventCount(pubSubMessage.getEventCount())
					.eventType(pubSubMessage.getEventType())
					.lastAttempted(Date.from(fixedClock.instant()))
					.reason("Success")
					.wasSuccessful(true)
					.build());
	}
	
	@Test
	void save_shouldReturn_savedEntry() {
		PubSubMessage pubSubMessage = PubSubMessage.builder()
				.eventCount(1L)
				.eventType(EventType.ORGANIZATION_CHANGE)
				.build();
		
		Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(1626825600000L), ZoneId.of("UTC"));
		Mockito.when(systemUtcClock.instant()).thenReturn(fixedClock.instant());

		EventRequestLog createdEntry = eventRequestService.createEventRequestLogEntry(subscriber, pubSubMessage, true, "Success");
		
		Mockito.when(eventRequestLogRepo.save(Mockito.any(EventRequestLog.class))).thenReturn(createdEntry);
		
		assertThat(eventRequestService.save(createdEntry))
			.usingRecursiveComparison()
			.ignoringFields("id")
			.isEqualTo(createdEntry);
	}
	
	@Test
	void saveAll_shouldReturn_allSavedEntries() {
		PubSubMessage pubSubMessage = PubSubMessage.builder()
				.eventCount(1L)
				.eventType(EventType.ORGANIZATION_CHANGE)
				.build();
		
		Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(1626825600000L), ZoneId.of("UTC"));
		Mockito.when(systemUtcClock.instant()).thenReturn(fixedClock.instant());
		
		EventRequestLog createdEntry = eventRequestService.createEventRequestLogEntry(subscriber, pubSubMessage, true, "Success");
		EventRequestLog createdEntry1 = eventRequestService.createEventRequestLogEntry(subscriber, pubSubMessage, true, "Success");
		
		var entries = List.of(createdEntry, createdEntry1);
		Mockito.when(eventRequestLogRepo.saveAll(Mockito.anyList())).thenReturn(entries);
		
		assertThat(eventRequestService.saveAll(entries))
			.usingRecursiveComparison()
			.ignoringFields("id")
			.ignoringCollectionOrder()
			.isEqualTo(entries);
	}
	
	@Test
	void createAndSaveEventRequestLogEntry_shouldReturn_savedEntry() {
		PubSubMessage pubSubMessage = PubSubMessage.builder()
				.eventCount(1L)
				.eventType(EventType.ORGANIZATION_CHANGE)
				.build();
		
		Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(1626825600000L), ZoneId.of("UTC"));
		Mockito.when(systemUtcClock.instant()).thenReturn(fixedClock.instant());
		
		EventRequestLog createdEntry = eventRequestService.createEventRequestLogEntry(subscriber, pubSubMessage, true, "Success");
		Mockito.when(eventRequestLogRepo.save(Mockito.any(EventRequestLog.class))).thenReturn(createdEntry);
		
		assertThat(eventRequestService.createAndSaveEventRequestLogEntry(subscriber, pubSubMessage, true, "Success"))
			.usingRecursiveComparison()
			.ignoringFields("id")
			.isEqualTo(createdEntry);
	}
	
	@Test
	void convertToDto_shouldReturn_dtoOfEntity() {
		EventRequestLog entity = eventRequestLogEntries.get(0);
		EventRequestLogDto dto = EventRequestLogDto.builder()
				.appClientUser(AppClientSummaryDto.builder()
						.id(appClientUser.getId())
						.name(appClientUser.getName())
						.build())
				.eventCount(entity.getEventCount())
				.eventType(entity.getEventType())
				.lastAttempted(entity.getLastAttempted())
				.reason(entity.getReason())
				.wasSuccessful(entity.isWasSuccessful())
				.build();
		
		assertThat(eventRequestService.convertToDto(entity)).isEqualTo(dto);
	}
}

package mil.tron.commonapi.service.pubsub.log;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import mil.tron.commonapi.dto.mapper.DtoMapper;
import mil.tron.commonapi.dto.pubsub.log.EventRequestLogDto;
import mil.tron.commonapi.entity.appsource.App;
import mil.tron.commonapi.entity.pubsub.Subscriber;
import mil.tron.commonapi.entity.pubsub.log.EventRequestLog;
import mil.tron.commonapi.pubsub.messages.PubSubMessage;
import mil.tron.commonapi.repository.filter.FilterCondition;
import mil.tron.commonapi.repository.filter.FilterCriteria;
import mil.tron.commonapi.repository.filter.QueryOperator;
import mil.tron.commonapi.repository.filter.SpecificationBuilder;
import mil.tron.commonapi.repository.pubsub.log.EventRequestLogRepository;

@Service
public class EventRequestServiceImpl implements EventRequestService {
	private static final DtoMapper OBJECT_MAPPER = new DtoMapper();
	
	private final EventRequestLogRepository eventRequestLogRepo;
	private final Clock systemUtcClock;
	
	public EventRequestServiceImpl(EventRequestLogRepository eventRequestLogRepo, Clock systemUtcClock) {
		this.eventRequestLogRepo = eventRequestLogRepo;
		this.systemUtcClock = systemUtcClock;
	}

	@Override
	public Page<EventRequestLogDto> getAllPaged(Pageable pageable) {
		return eventRequestLogRepo.findAll(pageable).map(this::convertToDto);
	}
	
	@Override
	public Page<EventRequestLogDto> getAllPagedWithSpec(Pageable pageable, List<FilterCriteria> filterCriteria) {
		Specification<EventRequestLog> spec = SpecificationBuilder.getSpecificationFromFilters(filterCriteria);
		Page<EventRequestLog> pagedResponse = eventRequestLogRepo.findAll(spec, pageable);
		
		return pagedResponse.map(this::convertToDto);
	}

	@Override
	public Page<EventRequestLogDto> getByAppIdPaged(Pageable pageable, UUID appClientId) {
		return eventRequestLogRepo.findAllByAppClientUser_Id(pageable, appClientId).map(this::convertToDto);
	}
	
	@Override
	public Page<EventRequestLogDto> getByAppIdPagedWithSpec(Pageable pageable, UUID appClientId,
			List<FilterCriteria> filterCriteria) {
		// Remove any filter on appClientUser because this is fetching by App Client ID
		purgeFilterCriteriaOfAppClientUserField(filterCriteria);
		
		// Add condition to filter on App Client ID
		filterCriteria.add(FilterCriteria
				.builder()
				.field(String.format("%s.%s", EventRequestLog.APP_CLIENT_USER_FIELD, App.ID_FIELD))
				.conditions(List.of(
						FilterCondition.builder()
							.operator(QueryOperator.EQUALS)
							.value(appClientId.toString())
							.build()
					))
				.build());
		
		Specification<EventRequestLog> spec = SpecificationBuilder.getSpecificationFromFilters(filterCriteria);
		return eventRequestLogRepo.findAll(spec, pageable).map(this::convertToDto);
	}

	@Override
	public Page<EventRequestLogDto> getByAppNamePaged(Pageable pageable, String appClientName) {
		return eventRequestLogRepo.findAllByAppClientUser_NameAsLower(pageable, appClientName.toLowerCase()).map(this::convertToDto);
	}

	@Override
	public Page<EventRequestLogDto> getByAppNamePagedWithSpec(Pageable pageable, String appClientName,
			List<FilterCriteria> filterCriteria) {
		// Remove any filter on appClientUser because this is fetching by App Client Name
		purgeFilterCriteriaOfAppClientUserField(filterCriteria);
		
		// Add condition to filter on App Client Name
		filterCriteria.add(FilterCriteria
				.builder()
				.field(String.format("%s.%s", EventRequestLog.APP_CLIENT_USER_FIELD, App.NAME_FIELD))
				.conditions(List.of(
						FilterCondition.builder()
							.operator(QueryOperator.EQUALS)
							.value(appClientName)
							.build()
					))
				.build());
		
		Specification<EventRequestLog> spec = SpecificationBuilder.getSpecificationFromFilters(filterCriteria);
		return eventRequestLogRepo.findAll(spec, pageable).map(this::convertToDto);
	}
	
	@Override
	public EventRequestLog createEventRequestLogEntry(Subscriber subscriber, PubSubMessage message,
			boolean wasSuccessful, String reason) {
		return EventRequestLog.builder()
				.appClientUser(subscriber.getAppClientUser())
				.eventType(message.getEventType())
				.eventCount(message.getEventCount())
				.lastAttempted(Date.from(Instant.now(systemUtcClock)))
				.wasSuccessful(wasSuccessful)
				.reason(reason)
				.build();
	}

	@Override
	public EventRequestLog save(EventRequestLog log) {
		return eventRequestLogRepo.save(log);
	}

	@Override
	public Iterable<EventRequestLog> saveAll(List<EventRequestLog> logs) {
		return eventRequestLogRepo.saveAll(logs);
	}

	@Override
	public EventRequestLog createAndSaveEventRequestLogEntry(Subscriber subscriber, PubSubMessage pubSubMessage,
			boolean wasSuccessful, String reason) {
		EventRequestLog entry = createEventRequestLogEntry(subscriber, pubSubMessage, wasSuccessful, reason);
		
		return save(entry);
	}
	
	@Override
	public EventRequestLogDto convertToDto(EventRequestLog eventRequestLog) {
		return OBJECT_MAPPER.map(eventRequestLog, EventRequestLogDto.class);
	}
	
	/**
	 * Removes any filter criteria targeting {@link EventRequestLog#APP_CLIENT_USER_FIELD} field.
	 * Happens in place.
	 * @param filterCriteria list of filter criteria
	 */
	private void purgeFilterCriteriaOfAppClientUserField(List<FilterCriteria> filterCriteria) {
		filterCriteria.removeIf(criteria -> 
			criteria.getField().contains(EventRequestLog.APP_CLIENT_USER_FIELD));
	}
}

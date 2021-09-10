package mil.tron.commonapi.service.pubsub.log;


import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import mil.tron.commonapi.dto.pubsub.log.EventRequestLogDto;
import mil.tron.commonapi.entity.pubsub.Subscriber;
import mil.tron.commonapi.entity.pubsub.log.EventRequestLog;
import mil.tron.commonapi.pubsub.messages.PubSubMessage;
import mil.tron.commonapi.repository.filter.FilterCriteria;

public interface EventRequestService {
	Page<EventRequestLogDto> getAllPaged(Pageable pageable);
	Page<EventRequestLogDto> getAllPagedWithSpec(Pageable pageable, List<FilterCriteria> filterCriteria);
	
	Page<EventRequestLogDto> getByAppIdPaged(Pageable pageable, UUID appClientId);
	Page<EventRequestLogDto> getByAppIdPagedWithSpec(Pageable pageable, UUID appClientId, List<FilterCriteria> filterCriteria);
	
	Page<EventRequestLogDto> getByAppNamePaged(Pageable pageable, String appClientName);
	Page<EventRequestLogDto> getByAppNamePagedWithSpec(Pageable pageable, String appClientName, List<FilterCriteria> filterCriteria);
	
	EventRequestLog createEventRequestLogEntry(Subscriber subscriber, PubSubMessage pubSubMessage, boolean wasSuccessful, String reason);
	EventRequestLog createAndSaveEventRequestLogEntry(Subscriber subscriber, PubSubMessage pubSubMessage, boolean wasSuccessful, String reason);
	EventRequestLog save(EventRequestLog log);
	Iterable<EventRequestLog> saveAll(List<EventRequestLog> logs);
	EventRequestLogDto convertToDto(EventRequestLog eventRequestLog);
}

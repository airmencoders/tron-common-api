package mil.tron.commonapi.repository.pubsub.log;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.CrudRepository;

import mil.tron.commonapi.entity.pubsub.log.EventRequestLog;

public interface EventRequestLogRepository extends CrudRepository<EventRequestLog, UUID> {
	Slice<EventRequestLog> findBy(Pageable page);
	Slice<EventRequestLog> findBy(Specification<EventRequestLog> spec, Pageable pageable);
	
	Page<EventRequestLog> findAll(Pageable page);
	Page<EventRequestLog> findAll(Specification<EventRequestLog> spec, Pageable pageable);
	
	Page<EventRequestLog> findAllByAppClientUser_Id(Pageable page, UUID id);
	Page<EventRequestLog> findAllByAppClientUser_NameAsLower(Pageable page, String appClientName);
}

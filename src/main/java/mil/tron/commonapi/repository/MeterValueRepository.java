package mil.tron.commonapi.repository;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.CrudRepository;

import mil.tron.commonapi.entity.MeterValue;

public interface MeterValueRepository extends CrudRepository<MeterValue, UUID>{
    List<MeterValue> findAllByAppEndpointIdAndTimestampBetweenOrderByTimestampDesc(UUID id, Date startDate, Date endDate);

}

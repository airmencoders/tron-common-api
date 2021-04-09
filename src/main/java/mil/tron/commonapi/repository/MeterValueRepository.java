package mil.tron.commonapi.repository;

import java.util.UUID;

import org.springframework.data.repository.CrudRepository;

import mil.tron.commonapi.entity.MeterValue;

public interface MeterValueRepository extends CrudRepository<MeterValue, UUID>{
    
}

package mil.tron.commonapi.repository;

import mil.tron.commonapi.entity.PersonMetadata;
import org.springframework.data.repository.CrudRepository;


public interface PersonMetadataRepository extends CrudRepository<PersonMetadata, PersonMetadata.PersonMetadataPK> {
}

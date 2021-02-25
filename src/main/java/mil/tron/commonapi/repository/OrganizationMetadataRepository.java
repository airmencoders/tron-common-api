package mil.tron.commonapi.repository;

import mil.tron.commonapi.entity.OrganizationMetadata;
import org.springframework.data.repository.CrudRepository;

public interface OrganizationMetadataRepository extends CrudRepository<OrganizationMetadata, OrganizationMetadata.OrganizationMetadataPK> {
}

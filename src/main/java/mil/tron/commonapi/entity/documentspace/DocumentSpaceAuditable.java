package mil.tron.commonapi.entity.documentspace;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@MappedSuperclass
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public abstract class DocumentSpaceAuditable {
	@Column(nullable = false, updatable = false)
	protected String createdBy;

	protected String lastModifiedBy;
	
	@Column(nullable = false, updatable = false)
	@Temporal(TemporalType.TIMESTAMP)
    protected Date createdOn;

	@Temporal(TemporalType.TIMESTAMP)
	protected Date lastModifiedOn;
}

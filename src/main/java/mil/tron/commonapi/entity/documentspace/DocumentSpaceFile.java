package mil.tron.commonapi.entity.documentspace;

import java.util.Date;
import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class DocumentSpaceFile extends DocumentSpaceAuditable {
	@Id
	@Builder.Default
	private UUID id = UUID.randomUUID();
	
	@NotNull
	@Size(min = 1, max = 255)
	@EqualsAndHashCode.Exclude
	private String fileName;
	
	@NotNull
	@EqualsAndHashCode.Exclude
	private long fileSize;
	
	@NotNull
	@Size(min = 1, max = 255)
	@EqualsAndHashCode.Exclude
	private String etag;
	
	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@EqualsAndHashCode.Exclude
	private DocumentSpaceFileSystemEntry parentFolder;
	
	@NotNull
	@Builder.Default
	@EqualsAndHashCode.Exclude
	private boolean isDeleteArchived = false;
	
	private String getCurrentAuditor() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		
		if (auth == null || !auth.isAuthenticated()) {
			return "Unknown";
		}
		
		return auth.getName();
	}
	
	@PrePersist
	private void onPrePersist() {
		super.setCreatedOn(new Date());
		super.setCreatedBy(getCurrentAuditor());
	}
	
	@PreUpdate
	private void onPreUpdate() {
		super.setLastModifiedOn(new Date());
		super.setLastModifiedBy(getCurrentAuditor());
	}
}

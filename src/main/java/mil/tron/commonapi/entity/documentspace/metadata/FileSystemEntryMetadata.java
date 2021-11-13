package mil.tron.commonapi.entity.documentspace.metadata;

import java.util.Date;
import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import lombok.Data;
import lombok.EqualsAndHashCode;
import mil.tron.commonapi.entity.DashboardUser;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;


@Entity
@Table(uniqueConstraints = { @UniqueConstraint(columnNames = {"file_system_entry_id", "dashboard_user_id"}) })
@Data
public class FileSystemEntryMetadata {
	@Id
	private UUID id;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "file_system_entry_id")
	@OnDelete(action = OnDeleteAction.CASCADE)
	@EqualsAndHashCode.Exclude
	private DocumentSpaceFileSystemEntry fileSystemEntry;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "dashboard_user_id")
	@OnDelete(action = OnDeleteAction.CASCADE)
	@EqualsAndHashCode.Exclude
	private DashboardUser dashboardUser;
	
	@EqualsAndHashCode.Exclude
	private Date lastDownloaded;
}

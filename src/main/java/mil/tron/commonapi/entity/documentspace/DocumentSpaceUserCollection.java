package mil.tron.commonapi.entity.documentspace;

import lombok.*;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@Table(name = "document_space_user_collection")
public class DocumentSpaceUserCollection {
	@Id
	@Builder.Default
	private UUID id = UUID.randomUUID();
	
	@NotBlank
	@NotNull
	@Size(max = 255)
	private String name;

	@NotNull
	private UUID documentSpaceId;

	@NotNull
	private UUID dashboardUserId;

	@ManyToMany
	@JoinTable(
			name="document_space_user_collection_entries",
    		joinColumns=@JoinColumn(name="user_collection_id", referencedColumnName="id"),
    		inverseJoinColumns=@JoinColumn(name="file_system_entry_id", referencedColumnName="id")
		)
	@Builder.Default
	@EqualsAndHashCode.Exclude
	private Set<DocumentSpaceFileSystemEntry> entries = new HashSet<>();
}

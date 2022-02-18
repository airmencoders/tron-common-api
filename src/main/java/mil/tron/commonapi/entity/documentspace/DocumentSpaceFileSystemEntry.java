package mil.tron.commonapi.entity.documentspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Table(name = "file_system_entries",
        // these constraints ensure we don't have the same folder name (or filename) at the same path level (same parent)
        //  in a given doc space - but we do allow duplicates if one of the items is in "archived" state
        uniqueConstraints={@UniqueConstraint(columnNames = {
            "doc_space_id",
            "parent_entry_id",
            "item_name",
            "is_delete_archived"
        }
)})
public class DocumentSpaceFileSystemEntry {

    /**
     * The universal NULL UUID value - since db's cant have 'NULL' as absence of value for 'uuid' datatype
     */
    public static final UUID NIL_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @NotNull
    @Column(name="doc_space_id", nullable = false)
    private UUID documentSpaceId;

    @Column(name="parent_entry_id", nullable = false)
    @Builder.Default
    private UUID parentEntryId = NIL_UUID;

    @NotNull
    @Builder.Default
    private boolean isFolder = true;

    @NotBlank
    @NotNull
    @Column(name="item_name", nullable = false)
    private String itemName;

    @Builder.Default
    @NotNull
    @Column(name="item_id", nullable = false)
    private UUID itemId = UUID.randomUUID();

    @NotNull
    @Builder.Default
	private long size = 0L;

	@NotNull
	@Size(min = 1, max = 255)
	private String etag;

	@Column(name="is_delete_archived", nullable = false)
	@NotNull
	@Builder.Default
	private boolean isDeleteArchived = false;

	@NotNull
    @Column(nullable = false, updatable = false)
	private String createdBy;

	private String lastModifiedBy;

	@NotNull
	@Column(nullable = false, updatable = false)
	@Temporal(TemporalType.TIMESTAMP)
	private Date createdOn;

	@Transient
    boolean hasNonArchivedContents;

    /**
     * Last modified date of the file, hopefully given
     * by the uploaded client application so that it can track
     * with the last mod date of the orginal file, otherwise
     * we'll decide the date ourselves - which will likely coincide
     * with the last activity date
     */
	@Temporal(TemporalType.TIMESTAMP)
	private Date lastModifiedOn;

    /**
     * Indicates last time this file was created or updated
     * Hibernate will change this for us automagically on updates
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastActivity;

	private String getCurrentAuditor() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();

		if (auth == null || !auth.isAuthenticated()) {
			return "Unknown";
		}

		return auth.getName();
	}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DocumentSpaceFileSystemEntry that = (DocumentSpaceFileSystemEntry) o;
        return documentSpaceId.equals(that.documentSpaceId) && parentEntryId.equals(that.parentEntryId) && itemName.equals(that.itemName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(documentSpaceId, parentEntryId, itemName);
    }

    /**
     * Actions on a new record/row
     */
    @PrePersist
    void checkParentUUID() {
        if (this.parentEntryId == null) {
            this.parentEntryId = NIL_UUID;
        }

        setCreatedOn(new Date());
		setCreatedBy(getCurrentAuditor());

		// last activity will be the creation date in this case
		setLastActivity(this.getCreatedOn());
    }

    /**
     * Actions on an update to an existing record/row
     */
    @PreUpdate
	private void onPreUpdate() {
    	if (this.parentEntryId == null) {
            this.parentEntryId = NIL_UUID;
        }

    	// respect if the last mod'd date is already populated by the client app
        //  so as to preserve some resemblance of a version control...
    	if (this.lastModifiedOn == null) {
            setLastModifiedOn(new Date());
        }

    	setLastActivity(new Date());
		setLastModifiedBy(getCurrentAuditor());
	}
}

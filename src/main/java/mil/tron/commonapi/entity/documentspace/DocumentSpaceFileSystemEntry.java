package mil.tron.commonapi.entity.documentspace;

import com.sun.istack.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import java.util.Objects;
import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Table(name = "file_system_entries", uniqueConstraints={@UniqueConstraint(columnNames = { "doc_space_id", "parent_entry_id", "item_name" })})
public class DocumentSpaceFileSystemEntry {

    /**
     * The universal NULL UUID value - since db's cant have 'NULL' as absence of value for 'uuid' datatype
     */
    public static final String NIL_UUID = "00000000-0000-0000-0000-000000000000";

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @NotNull
    @Column(name="doc_space_id", nullable = false)
    private UUID documentSpaceId;

    @Column(name="parent_entry_id", nullable = false)
    @Builder.Default
    private UUID parentEntryId = UUID.fromString(NIL_UUID);

    @NotBlank
    @NotNull
    @Column(name="item_name", nullable = false)
    private String itemName;

    @Builder.Default
    @NotNull
    @Column(name="item_id", nullable = false)
    private UUID itemId = UUID.randomUUID();

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

    @PrePersist
    @PreUpdate
    void checkParentUUID() {
        if (this.parentEntryId == null) {
            this.parentEntryId = UUID.fromString(NIL_UUID);
        }
    }
}

package mil.tron.commonapi.service.documentspace;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class FileSystemElementTree {
    private DocumentSpaceFileSystemEntry value;

    @Builder.Default
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<FileSystemElementTree> nodes = new ArrayList<>();

    public void addNode(FileSystemElementTree entry) {
        this.nodes.add(entry);
    }

    @Override
    public String toString() {
        return this.value.getItemName();
    }
}

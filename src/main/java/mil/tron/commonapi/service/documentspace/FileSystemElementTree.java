package mil.tron.commonapi.service.documentspace;

import com.amazonaws.services.s3.model.S3Object;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FileSystemElementTree {

    @Getter
    @Setter
    private DocumentSpaceFileSystemEntry value;

    @Getter
    @Setter
    @Builder.Default
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<FileSystemElementTree> nodes = new ArrayList<>();  // these are folders (sub folders)

    @Getter
    @Setter
    private List<S3Object> files = new ArrayList<>();

    public void addNode(FileSystemElementTree entry) {
        this.nodes.add(entry);
    }
}

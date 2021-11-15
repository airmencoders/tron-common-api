package mil.tron.commonapi.entity.documentspace.metadata;

import lombok.Value;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;

@Value
public class FileSystemEntryWithMetadata {
	private DocumentSpaceFileSystemEntry fileEntry;
	private FileSystemEntryMetadata metadata;
}

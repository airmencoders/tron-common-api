package mil.tron.commonapi.service.documentspace.util;

import lombok.*;
import lombok.experimental.SuperBuilder;
import mil.tron.commonapi.entity.documentspace.DocumentSpaceFileSystemEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Like FilePathSpec, but we include the sub dirs contained herein and files
 */
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class FilePathSpecWithContents extends FilePathSpec {
	  @Getter
	  @Setter
	  @Builder.Default
	  private List<DocumentSpaceFileSystemEntry> entries = new ArrayList<>();
}

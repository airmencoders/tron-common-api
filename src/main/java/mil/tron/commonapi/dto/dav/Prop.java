package mil.tron.commonapi.dto.dav;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Builder;
import lombok.Data;

import java.util.Optional;

@Data
@Builder
public class Prop {

    @JacksonXmlProperty(localName = "resourcetype")
    private PropResourceType resourceType;

    @JacksonXmlProperty(localName = "creationdate")
    private String creationDate;

    @JacksonXmlProperty(localName = "getlastmodified")
    private String lastModified;

    @JacksonXmlProperty(localName = "getcontentlength")
    private Optional<Long> contentLength;
}

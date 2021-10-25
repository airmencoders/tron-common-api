package mil.tron.commonapi.dto.dav;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Builder;
import lombok.Data;

import java.util.Optional;

@Data
@Builder
public class Prop {

    @JacksonXmlProperty(localName = "D:resourcetype")
    private PropResourceType resourceType;

    @JacksonXmlProperty(localName = "D:creationdate")
    private String creationDate;

    @JacksonXmlProperty(localName = "D:getlastmodified")
    private String lastModified;

    @JacksonXmlProperty(localName = "D:getcontentlength")
    private Optional<Long> contentLength;
}

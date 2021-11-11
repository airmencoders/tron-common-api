package mil.tron.commonapi.dto.dav;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
public class PropResourceType {

    @JacksonXmlProperty(localName = "D:collection")
    private String collection;
}

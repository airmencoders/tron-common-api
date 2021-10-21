package mil.tron.commonapi.dto.dav;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public class PropResourceType {

    @JacksonXmlProperty(localName = "collection")
    private boolean collection;
}

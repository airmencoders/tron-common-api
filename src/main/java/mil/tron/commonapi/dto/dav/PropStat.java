package mil.tron.commonapi.dto.dav;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class PropStat {

    @JacksonXmlProperty(localName = "D:prop")
    private Prop prop;

    @JacksonXmlProperty(localName = "D:status")
    private String status;

}

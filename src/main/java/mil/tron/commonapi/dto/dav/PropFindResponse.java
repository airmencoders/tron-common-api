package mil.tron.commonapi.dto.dav;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PropFindResponse {

    @JacksonXmlProperty(localName = "D:href")
    private String href;

    @JacksonXmlProperty(localName = "D:propstat")
    private PropStat propStat;
}

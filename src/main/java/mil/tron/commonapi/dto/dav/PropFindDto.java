package mil.tron.commonapi.dto.dav;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@JacksonXmlRootElement(localName = "multistatus", namespace = "D")
@Data
@Builder
public class PropFindDto {

    @JacksonXmlElementWrapper(useWrapping = false)
    private List<PropFindResponse> responses;

}

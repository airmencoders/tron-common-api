package mil.tron.commonapi.dto.dav;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;

@JacksonXmlRootElement(localName = "multistatus", namespace = "D")
@Data
@Builder
public class PropFindDto {

    @JacksonXmlProperty(localName = "D:response")
    @JacksonXmlElementWrapper(useWrapping = false)
    @Singular
    private List<PropFindResponse> responses;

}

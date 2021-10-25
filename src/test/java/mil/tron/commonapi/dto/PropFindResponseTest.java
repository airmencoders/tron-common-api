package mil.tron.commonapi.dto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import mil.tron.commonapi.dto.dav.*;
import org.junit.jupiter.api.Test;

import java.util.List;

public class PropFindResponseTest {

    @Test
    void testSerialization() throws Exception {
        ObjectMapper xmlMapper = new XmlMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        PropFindDto propFindResponse = PropFindDto.builder()
                .response(PropFindResponse.builder()
                        .href("http://link")
                        .propStat(PropStat.builder()
                                .prop(Prop.builder()
                                        .resourceType(new PropResourceType(null))
                                        .creationDate("2021-10-05T19:44:32+00:00")
                                        .lastModified("")
                                .build())
                                .status("HTTP/1.1 200 OK")
                                .build())
                        .build())
                .build();

        String resultString = xmlMapper.writer().writeValueAsString(propFindResponse);

        System.out.println(resultString);
    }
}

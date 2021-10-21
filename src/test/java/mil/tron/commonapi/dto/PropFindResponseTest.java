package mil.tron.commonapi.dto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import mil.tron.commonapi.dto.dav.PropFindDto;
import mil.tron.commonapi.dto.dav.PropFindResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

public class PropFindResponseTest {

    @Test
    void testSerialization() throws Exception {
        ObjectMapper xmlMapper = new XmlMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        PropFindDto propFindResponse = PropFindDto.builder()
                .responses(List.of(
                        PropFindResponse.builder()
                        .href("http://link")
                        .build()
                )).build();

        String resultString = xmlMapper.writer().writeValueAsString(propFindResponse);

        System.out.println(resultString);
    }
}

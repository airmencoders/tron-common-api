package mil.tron.commonapi.controller.documentspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.service.documentspace.DocumentSpaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties", properties = "minio.enabled=true")
@AutoConfigureMockMvc
class DocumentSpaceControllerTest {
    private static final String ENDPOINT = "/v2/document-space/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentSpaceService documentSpaceService;

    private UUID documentSpaceId;
    
    
    @BeforeEach
    public void setUp(){
        documentSpaceId = UUID.randomUUID();
    }

    @Test
    void removesDashboardUserFromDocumentSpace() throws Exception {

        String userEmail = "dev@tron.dev";

        Mockito.doNothing().when(documentSpaceService).removeDashboardUserFromDocumentSpace(documentSpaceId, userEmail);

        mockMvc.perform(delete(ENDPOINT +"spaces/"+ "{id}"+"/users", documentSpaceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(userEmail)))
                .andExpect(status().isNoContent());
    }
}
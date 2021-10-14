package mil.tron.commonapi.controller.documentspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.service.documentspace.DocumentSpaceService;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.FileInputStream;
import java.util.*;

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

    ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    public void setUp(){
        documentSpaceId = UUID.randomUUID();
    }

    @Test
    void removesDashboardUserFromDocumentSpace() throws Exception {

        String userEmail = "dev@tron.dev";

        Mockito.doNothing().when(documentSpaceService).removeDashboardUserFromDocumentSpace(documentSpaceId, userEmail);

        mockMvc.perform(delete(ENDPOINT +"spaces/"+ "{id}"+"/users/dashboard", documentSpaceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(userEmail)))
                .andExpect(status().isNoContent());
    }

    @Test
    void batchAddUsersToDocumentSpace() throws Exception {

        FileInputStream filestream = new FileInputStream("src/test/resources/dashboard-user-csv/happy-case.csv");
        MockMultipartFile file = new MockMultipartFile("file", "happy-case.csv","text/csv" , filestream);

        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart(ENDPOINT +"spaces/"+ "{id}" + "/batchUsers", documentSpaceId).file(file);

        mockMvc.perform(builder).andExpect(status().isOk());
    }

    @Test
    void batchAddUsersToDocumentSpaceAndReturnsExceptions() throws Exception {

        FileInputStream filestream = new FileInputStream("src/test/resources/dashboard-user-csv/happy-case.csv");
        MockMultipartFile file = new MockMultipartFile("file", "happy-case.csv","text/csv", filestream);


        Mockito.doReturn(Collections.singletonList("bad header at line 0")).when(documentSpaceService).batchAddDashboardUserToDocumentSpace(documentSpaceId, file);

        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart(ENDPOINT +"spaces/"+ "{id}" + "/batchUsers", documentSpaceId).file(file);

        MvcResult response = mockMvc.perform(builder).andExpect(status().isOk()).andReturn();

        List<String> result = mapper.readValue(response.getResponse().getContentAsString(), List.class);


        Assert.assertEquals(1, result.size());
    }
}
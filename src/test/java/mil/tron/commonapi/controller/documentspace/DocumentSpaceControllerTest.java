package mil.tron.commonapi.controller.documentspace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import mil.tron.commonapi.dto.documentspace.*;
import mil.tron.commonapi.dto.documentspace.mobile.DocumentMobileDto;
import mil.tron.commonapi.dto.response.pagination.Pagination;
import mil.tron.commonapi.dto.response.pagination.PaginationLink;
import mil.tron.commonapi.dto.response.pagination.PaginationWrappedResponse;
import mil.tron.commonapi.service.documentspace.DocumentSpaceService;
import mil.tron.commonapi.service.documentspace.util.FilePathSpec;
import mil.tron.commonapi.service.documentspace.util.FilePathSpecWithContents;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.FileInputStream;
import java.security.Principal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

        String[] userEmails = new String[] {"dev@tron.dev"};

        Mockito.doNothing().when(documentSpaceService).removeDashboardUserFromDocumentSpace(documentSpaceId, userEmails);

        mockMvc.perform(delete(ENDPOINT +"spaces/"+ "{id}"+"/users/dashboard", documentSpaceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(userEmails)))
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

    @Test
    void testFolderCreateEndpoint() throws Exception {

        Mockito.when(documentSpaceService.createFolder(Mockito.any(UUID.class), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(FilePathSpec.builder().build());

        mockMvc.perform(post(ENDPOINT +"spaces/{id}/folders", documentSpaceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(DocumentSpaceCreateFolderDto.builder()
                    .folderName("newfolder")
                    .path("/")
                    .build())))
                .andExpect(status().isCreated());
    }

    @Test
    void testFolderListEndpoint() throws Exception {

        Mockito.when(documentSpaceService.getFolderContents(Mockito.any(UUID.class), Mockito.anyString()))
                .thenReturn(FilePathSpecWithContents.builder().build());

        mockMvc.perform(get(ENDPOINT +"spaces/{id}/contents", documentSpaceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(DocumentSpacePathDto.builder()
                        .path("/newfolder")
                        .build())))
                .andExpect(status().isOk());
    }

    @Test
    void testFolderRenameEndpoint() throws Exception {

        Mockito.doNothing().when(documentSpaceService).renameFolder(Mockito.any(UUID.class), Mockito.anyString(), Mockito.anyString());
        mockMvc.perform(put(ENDPOINT +"spaces/{id}/folders", documentSpaceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(DocumentSpaceRenameFolderDto.builder()
                        .existingFolderPath("/newfolder")
                        .newName("oldfolder")
                        .build())))
                .andExpect(status().isNoContent());
    }
    
    @WithMockUser(username = "testuser")
    @Test
    void testGetRecentlyUploadedFiles() throws JsonProcessingException, Exception {
    	RecentDocumentDto documentDto = new RecentDocumentDto(UUID.randomUUID(), "testfile.txt", UUID.randomUUID(), new Date(), "", UUID.randomUUID(), "test document space");
    	
    	RecentDocumentDtoResponseWrapper response = new RecentDocumentDtoResponseWrapper();
    	response.setData(Arrays.asList(documentDto));
    	
    	Pageable pageable = PageRequest.of(0, 100);
    	Page<RecentDocumentDto> serviceResponse = new PageImpl<>(response.getData(), pageable, response.getData().size());
    	Mockito.when(documentSpaceService.getRecentlyUploadedFilesByAuthUser(Mockito.anyString(), Mockito.eq(pageable)))
    		.thenReturn(serviceResponse);
    	
    	PaginationWrappedResponse<List<RecentDocumentDto>> controllerResponse = PaginationWrappedResponse.<List<RecentDocumentDto>>builder()
    			.data(response.getData())
    			.pagination(new Pagination(0, 100, 1L, 1, new PaginationLink()))
    			.build();
    	
    	mockMvc.perform(get(ENDPOINT +"/spaces/files/recently-uploaded?page=0&size=100"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(controllerResponse)));
    }
    
    @Test
    void testDeleteArchiveItemBySpaceAndParent() throws JsonProcessingException, Exception {
    	Mockito.doNothing().when(documentSpaceService).archiveItem(Mockito.any(UUID.class), Mockito.any(UUID.class), Mockito.anyString());
    	
    	
    	mockMvc.perform(delete(ENDPOINT + "/spaces/{id}/folder/{parentFolderId}/file/{filename}/archive", UUID.randomUUID(), UUID.randomUUID(), "testfile"))
                .andExpect(status().isNoContent());
    }

    @WithMockUser(username = "testuser")
    @Test
    void testDocumentSpaceSearch() throws Exception {

        Mockito.when(documentSpaceService.findFilesInSpaceLike(Mockito.any(UUID.class), Mockito.anyString(), Mockito.any(Pageable.class), Mockito.any(Principal.class)))
                        .thenReturn(new PageImpl<>(Lists.newArrayList(DocumentMobileDto.builder()
                                        .key("sdfsdf")
                                        .spaceId(UUID.randomUUID().toString())
                                        .path("")
                                        .size(0L)
                                        .lastModifiedDate(new Date())
                                        .lastModifiedBy("me")
                                        .isFolder(false)
                                        .build()), Pageable.ofSize(10), 1L));

        mockMvc.perform(post(ENDPOINT + "/spaces/{id}/search", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(DocumentSpaceSearchDto.builder()
                        .query("somefile")
                        .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThan(0))));

        // test null query is 400
        mockMvc.perform(post(ENDPOINT + "/spaces/{id}/search", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // test blank query is 400
        mockMvc.perform(post(ENDPOINT + "/spaces/{id}/search", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBJECT_MAPPER.writeValueAsString(DocumentSpaceSearchDto.builder()
                                .query("")
                                .build())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(ENDPOINT + "/spaces/{id}/search", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBJECT_MAPPER.writeValueAsString(DocumentSpaceSearchDto.builder()
                                .query("    ")
                                .build())))
                .andExpect(status().isBadRequest());
    }
}
package mil.tron.commonapi.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.dto.DashboardUserDto;
import mil.tron.commonapi.dto.PrivilegeDto;
import mil.tron.commonapi.dto.response.WrappedResponse;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.service.AppClientUserPreAuthenticatedService;
import mil.tron.commonapi.service.DashboardUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.mockito.Mockito;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardUserController.class)
@WithMockUser(username = "DashboardUser", authorities = { "DASHBOARD_ADMIN", "DASHBOARD_USER" })
public class DashboardUserControllerTest {
    private static final String ENDPOINT = "/v1/dashboard-users/";
    private static final String ENDPOINT_V2 = "/v2/dashboard-users/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardUserService dashboardUserService;

    @MockBean
    private AppClientUserPreAuthenticatedService appClientUserPreAuthenticatedService;

    private DashboardUserDto testDashboardUserDto;
    private String testDashboardUserJson;
    private List<DashboardUserDto> dashboardUsers;
    private ModelMapper mapper = new ModelMapper();

    @BeforeEach
    public void beforeEachTest() throws JsonProcessingException {
        dashboardUsers = new ArrayList<>();
        testDashboardUserDto = new DashboardUserDto();
        testDashboardUserDto.setEmail("admin@mvc.com");
        Privilege priv = new Privilege((long)3,"DASHBOARD_ADMIN");
        List<PrivilegeDto> privileges = new ArrayList<>();
        privileges.add(mapper.map(priv, PrivilegeDto.class));
        testDashboardUserDto.setPrivileges(privileges);

        testDashboardUserJson = OBJECT_MAPPER.writeValueAsString(testDashboardUserDto);
        dashboardUsers.add(testDashboardUserDto);
    }

    @Nested
    @WithMockUser(username = "DashboardUser", authorities = { "DASHBOARD_ADMIN", "DASHBOARD_USER" })
    class GetDashboardUserTest {
        @Test
        void testGetAll() throws Exception {
            Mockito.when(dashboardUserService.getAllDashboardUsersDto()).thenReturn(dashboardUsers);

            mockMvc.perform(get(ENDPOINT))
                    .andExpect(status().isOk())
                    .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(dashboardUsers)));
            
            // V2
            WrappedResponse<List<DashboardUserDto>> wrappedResponse = WrappedResponse.<List<DashboardUserDto>>builder().data(dashboardUsers).build();
            mockMvc.perform(get(ENDPOINT_V2))
	            .andExpect(status().isOk())
	            .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(wrappedResponse)));
        }

        @Test
        void testGet() throws Exception {
            Mockito.when(dashboardUserService.getDashboardUserDto(Mockito.any(UUID.class))).thenReturn(testDashboardUserDto);

            mockMvc.perform(get(ENDPOINT + "{id}", testDashboardUserDto.getId()))
                    .andExpect(status().isOk())
                    .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(testDashboardUserDto)));
        }

        @Test
        void testGetByIdBadPathVariable() throws Exception {
            // Send an invalid UUID as ID path variable
            mockMvc.perform(get(ENDPOINT + "{id}", "asdf1234"))
                    .andExpect(status().isBadRequest())
                    .andExpect(result -> assertTrue(result.getResolvedException() instanceof MethodArgumentTypeMismatchException));
        }
        
        @Test
        void testGetSelfDashboardUserExists() throws Exception {
        	Mockito.when(dashboardUserService.getSelf(Mockito.anyString())).thenReturn(testDashboardUserDto);
        	
        	mockMvc.perform(get(ENDPOINT + "/self"))
	            .andExpect(status().isOk())
	            .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(testDashboardUserDto)));
        }
        
        @Test
        void testGetSelfDashboardUserNotExists() throws Exception {
        	Mockito.when(dashboardUserService.getSelf(Mockito.anyString())).thenThrow(new UsernameNotFoundException("not found"));
        	
        	mockMvc.perform(get(ENDPOINT + "/self"))
	            .andExpect(status().isForbidden());
        }
    }

    @Nested
    @WithMockUser(username = "DashboardUser", authorities = { "DASHBOARD_ADMIN", "DASHBOARD_USER" })
    class PostDashboardUserTest {
        @Test
        void testPost() throws Exception {
            Mockito.when(dashboardUserService.createDashboardUserDto(Mockito.any(DashboardUserDto.class))).thenReturn(testDashboardUserDto);

            mockMvc.perform(post(ENDPOINT)
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(testDashboardUserJson))
                    .andExpect(status().isCreated())
                    .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(testDashboardUserJson));
        }

        @Test
        void testPostInvalidJsonBody() throws Exception {
            // Send empty string as bad json data
            mockMvc.perform(post(ENDPOINT)
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(""))
                    .andExpect(status().isBadRequest())
                    .andExpect(result -> assertTrue(result.getResolvedException() instanceof HttpMessageNotReadableException));
        }
    }

    @Nested
    @WithMockUser(username = "DashboardUser", authorities = { "DASHBOARD_ADMIN", "DASHBOARD_USER" })
    class PutDashboardUserTest {
        @Test
        void testPut() throws Exception {
            Mockito.when(dashboardUserService.updateDashboardUserDto(Mockito.any(UUID.class), Mockito.any(DashboardUserDto.class))).thenReturn(testDashboardUserDto);

            mockMvc.perform(put(ENDPOINT + "{id}", testDashboardUserDto.getId())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(testDashboardUserJson))
                    .andExpect(status().isOk())
                    .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(testDashboardUserJson));
        }

        @Test
        void testPutInvalidJsonBody() throws Exception {
            // Send empty string as bad json data
            mockMvc.perform(put(ENDPOINT + "{id}", testDashboardUserDto.getId())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(""))
                    .andExpect(status().isBadRequest())
                    .andExpect(result -> assertTrue(result.getResolvedException() instanceof HttpMessageNotReadableException));
        }

        @Test
        void testPutInvalidBadPathVariable() throws Exception {
            // Send an invalid UUID as ID path variable
            mockMvc.perform(put(ENDPOINT + "{id}", "asdf1234"))
                    .andExpect(status().isBadRequest())
                    .andExpect(result -> assertTrue(result.getResolvedException() instanceof MethodArgumentTypeMismatchException));
        }

        @Test
        void testPutResourceDoesNotExist() throws Exception {
            Mockito.when(dashboardUserService.updateDashboardUserDto(Mockito.any(UUID.class), Mockito.any(DashboardUserDto.class))).thenThrow(new RecordNotFoundException("Record not found"));

            mockMvc.perform(put(ENDPOINT + "{id}", testDashboardUserDto.getId())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(testDashboardUserJson))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void testDelete() throws Exception {
        Mockito.doNothing().when(dashboardUserService).deleteDashboardUser(testDashboardUserDto.getId());

        mockMvc.perform(delete(ENDPOINT + "{id}", testDashboardUserDto.getId()))
                .andExpect(status().isNoContent());
    }
}

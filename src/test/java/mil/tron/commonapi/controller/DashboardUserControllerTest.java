package mil.tron.commonapi.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import mil.tron.commonapi.dto.DashboardUserDto;
import mil.tron.commonapi.dto.PersonDto;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.service.AppClientUserPreAuthenticatedService;
import mil.tron.commonapi.service.DashboardUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.parameters.P;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardUserController.class)
@WithMockUser(username = "DASHBOARD_ADMIN", authorities = { "DASHBOARD" })
public class DashboardUserControllerTest {
    private static final String ENDPOINT = "/v1/dashboard-users/";
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

    @BeforeEach
    public void beforeEachTest() throws JsonProcessingException {
        dashboardUsers = new ArrayList<>();
        testDashboardUserDto = new DashboardUserDto();
        testDashboardUserDto.setEmail("admin@mvc.com");
        Privilege priv = new Privilege((long)3,"DASHBOARD_ADMIN");
        HashSet<Privilege> privileges = new HashSet<>();
        privileges.add(priv);
        testDashboardUserDto.setPrivileges(privileges);

        testDashboardUserJson = OBJECT_MAPPER.writeValueAsString(testDashboardUserDto);
        dashboardUsers.add(testDashboardUserDto);
    }

    @Test
    void testGetAll() throws Exception {
        Mockito.when(dashboardUserService.getAllDashboardUsersDto()).thenReturn(dashboardUsers);

        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(dashboardUsers)));
    }

    @Test
    void testGet() throws Exception {
        Mockito.when(dashboardUserService.getDashboardUserDto(Mockito.any(UUID.class))).thenReturn(testDashboardUserDto);

        mockMvc.perform(get(ENDPOINT + "{id}", testDashboardUserDto.getId()))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(testDashboardUserDto)));
    }
}

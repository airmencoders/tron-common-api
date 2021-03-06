package mil.tron.commonapi.controller.appsource;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.val;
import mil.tron.commonapi.dto.AppClientUserPrivDto;
import mil.tron.commonapi.dto.DashboardUserDto;
import mil.tron.commonapi.dto.appclient.AppClientSummaryDto;
import mil.tron.commonapi.dto.appsource.AppEndPointPrivDto;
import mil.tron.commonapi.dto.appsource.AppSourceDetailsDto;
import mil.tron.commonapi.dto.appsource.AppSourceDto;
import mil.tron.commonapi.dto.response.WrappedResponse;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.appsource.AppEndpoint;
import mil.tron.commonapi.entity.appsource.AppEndpointPriv;
import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.service.AppClientUserPreAuthenticatedService;
import mil.tron.commonapi.service.AppClientUserService;
import mil.tron.commonapi.service.AppSourceService;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
public class AppSourceControllerTest {
	private static final String ENDPOINT = "/v1/app-source/";
	private static final String ENDPOINT_V2 = "/v2/app-source/";
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Autowired
	private MockMvc mockMvc;
	
	@MockBean
	private AppClientUserPreAuthenticatedService appClientUserPreAuthenticatedService;
	
	@MockBean
	private AppSourceService service;

	@MockBean
	private AppClientUserService appClientUserService;
	
	private static UUID APP_SOURCE_UUID = UUID.randomUUID();
    private static String APP_SOURCE_NAME = "Test App Source";
    private Set<AppEndpointPriv> appEndpointPrivs = new HashSet<>();
    private AppSource appSource;
    private AppSourceDetailsDto appSourceDetailsDto;
    private List<AppClientUserPrivDto> appClientUserPrivDtos;
    private Set<Privilege> privileges;
    private AppClientUser appClientUser;
    private List<AppSourceDto> appSourceDtos;
    private AppEndpoint appEndpoint;
	
	@BeforeEach
    void setup() {
    	privileges = new HashSet<>();
    	privileges.add(
			Privilege
    			.builder()
    			.id(1L)
    			.name("Read")
    			.build()
		);
    	privileges.add(
			Privilege
    			.builder()
    			.id(2L)
    			.name("Write")
    			.build()
		);
    	
        appSource = AppSource
                .builder()
                .id(APP_SOURCE_UUID)
                .name(APP_SOURCE_NAME)
                .build();
        appClientUser = AppClientUser
                .builder()
                .id(UUID.randomUUID())
                .name("Test App Client")
                .build();
        appEndpoint = AppEndpoint
                .builder()
                .id(UUID.randomUUID())
                .appSource(appSource)
                .build();
        val appEndpointPriv = AppEndpointPriv
                .builder()
                .id(UUID.randomUUID())
                .appSource(appSource)
                .appClientUser(appClientUser)
                .appEndpoint(appEndpoint)
                .build();
        appEndpointPrivs.add(
            appEndpointPriv
        );
        appSource.setAppPrivs(appEndpointPrivs);
        appClientUser.setAppEndpointPrivs(appEndpointPrivs);
        
        appClientUserPrivDtos = new ArrayList<>();
        appClientUserPrivDtos.add(
    		AppClientUserPrivDto
        		.builder()
        		.appClientUser(appClientUser.getId())
                .appClientUserName(appClientUser.getName())
                .appEndpoint(appEndpoint.getId())
                .privilege(appEndpoint.getPath())
        		.build()
		);
        
        appSourceDetailsDto = AppSourceDetailsDto
        		.builder()
        		.id(appSource.getId())
        		.name(appSource.getName())
        		.appClients(appClientUserPrivDtos)
        		.build();
        
        appSourceDtos = new ArrayList<>();
        appSourceDtos.add(AppSourceDto.builder().id(APP_SOURCE_UUID).name(APP_SOURCE_NAME).build());
    }
	
	@Nested
	@WithMockUser(username = "DashboardUser", authorities = { "DASHBOARD_ADMIN", "DASHBOARD_USER" })
	class Get {
		@Test
		void getAll() throws Exception {
			Mockito.when(service.getAppSources()).thenReturn(appSourceDtos);
			
			mockMvc.perform(get(ENDPOINT))
				.andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(appSourceDtos)));
			
			// V2
			WrappedResponse<List<AppSourceDto>> wrappedResponse = WrappedResponse.<List<AppSourceDto>>builder().data(appSourceDtos).build();
			mockMvc.perform(get(ENDPOINT_V2))
				.andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(wrappedResponse)));
		}	
		
		@Test
		void getAppSourceDetails() throws Exception {
			Mockito.when(service.getAppSource(Mockito.any(UUID.class))).thenReturn(appSourceDetailsDto);


			mockMvc.perform(get(ENDPOINT + "{id}", appSourceDetailsDto.getId()))
				.andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(appSourceDetailsDto)));
		}
		
		@Test
        void getByIdBadPathVariable() throws Exception {
            // Send an invalid UUID as ID path variable
            mockMvc.perform(get(ENDPOINT + "{id}", "asdf1234"))
                    .andExpect(status().isBadRequest())
                    .andExpect(result -> assertTrue(result.getResolvedException() instanceof MethodArgumentTypeMismatchException));
        }

        @Test
		void getAvailableAppClients() throws Exception {
			Mockito.when(appClientUserService.getAppClientUserSummaries()).thenReturn(Lists.newArrayList(
					AppClientSummaryDto.builder().name("Test").build()));

			mockMvc.perform(get(ENDPOINT + "app-clients"))
					.andExpect(status().isOk());
			
			// V2
			mockMvc.perform(get(ENDPOINT_V2 + "app-clients"))
				.andExpect(status().isOk());
		}

		@Test
		void getAPISpecResource() throws Exception {
			Resource resource = new ByteArrayResource(new byte[] { 'T', 'E', 'S', 'T'}, "description");
			Mockito.when(service.getApiSpecForAppSource(Mockito.any(UUID.class))).thenReturn(resource);

			mockMvc.perform(get(ENDPOINT + "spec/{appId}", appSource.getId()))
				.andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo("TEST"));
		}

		@Test
		void getAPISpecResourceBadPathIdVariable() throws Exception {
			mockMvc.perform(get(ENDPOINT + "spec/{appId}", "asdfasdf"))
				.andExpect(status().isBadRequest())
				.andExpect(result -> assertTrue(result.getResolvedException() instanceof MethodArgumentTypeMismatchException));
		}

		@Test
		void getAPISpecResourceByEndpointPriv() throws Exception {
			Resource resource = new ByteArrayResource(new byte[] { 'T', 'E', 'S', 'T'}, "description");
			Mockito.when(service.getApiSpecForAppSourceByEndpointPriv(Mockito.any(UUID.class))).thenReturn(resource);

			mockMvc.perform(get(ENDPOINT + "spec/endpoint-priv/{appId}", appSource.getId()))
				.andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo("TEST"));
		}

		
		@Test
		void getAPISpecResourceByEndpointPrivBadPathIdVariable() throws Exception {
			mockMvc.perform(get(ENDPOINT + "spec/endpoint-priv/{appId}", "asdfasdf"))
				.andExpect(status().isBadRequest())
				.andExpect(result -> assertTrue(result.getResolvedException() instanceof MethodArgumentTypeMismatchException));
		}
	}
	
	@Nested
    @WithMockUser(username = "DashboardUser", authorities = { "DASHBOARD_ADMIN", "DASHBOARD_USER" })
    class Post {
        @Test
        void successPost() throws Exception {
            Mockito.when(service.createAppSource(Mockito.any(AppSourceDetailsDto.class))).thenReturn(appSourceDetailsDto);

            mockMvc.perform(post(ENDPOINT)
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(appSourceDetailsDto)))
                    .andExpect(status().isCreated())
                    .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(appSourceDetailsDto)));
        }

        @Test
        void invalidJson() throws Exception {
            // Send empty string as bad json data
            mockMvc.perform(post(ENDPOINT)
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(""))
                    .andExpect(status().isBadRequest())
                    .andExpect(result -> assertTrue(result.getResolvedException() instanceof HttpMessageNotReadableException));
        }

        @Test
		void testAddClientEndpointPriv() throws Exception {
        	Mockito.when(service.addEndPointPrivilege(Mockito.any())).thenReturn(appSourceDetailsDto);
			mockMvc.perform(post(ENDPOINT + "app-clients")
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(OBJECT_MAPPER.writeValueAsString(AppEndPointPrivDto
						.builder()
						.appSourceId(UUID.randomUUID())
						.appEndpointId(UUID.randomUUID())
						.appClientUserId(UUID.randomUUID())
						.build())))
					.andExpect(status().isCreated());

		}
    }
	
	@Nested
    @WithMockUser(username = "DashboardUser", authorities = { "DASHBOARD_ADMIN", "DASHBOARD_USER" })
    class Put {
        @Test
        void success() throws Exception {
            Mockito.when(service.updateAppSource(Mockito.any(UUID.class), Mockito.any(AppSourceDetailsDto.class))).thenReturn(appSourceDetailsDto);

            mockMvc.perform(put(ENDPOINT + "{id}", appSourceDetailsDto.getId())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(appSourceDetailsDto)))
                    .andExpect(status().isOk())
                    .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(appSourceDetailsDto)));
        }

        @Test
        void invalidJson() throws Exception {
            // Send empty string as bad json data
            mockMvc.perform(put(ENDPOINT + "{id}", appSourceDetailsDto.getId())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(""))
                    .andExpect(status().isBadRequest())
                    .andExpect(result -> assertTrue(result.getResolvedException() instanceof HttpMessageNotReadableException));
        }

        @Test
        void badPathVariable() throws Exception {
            // Send an invalid UUID as ID path variable
            mockMvc.perform(put(ENDPOINT + "{id}", "asdf1234"))
                    .andExpect(status().isBadRequest())
                    .andExpect(result -> assertTrue(result.getResolvedException() instanceof MethodArgumentTypeMismatchException));
        }

        @Test
        void resourceNotExists() throws Exception {
        	Mockito.when(service.updateAppSource(Mockito.any(UUID.class), Mockito.any(AppSourceDetailsDto.class))).thenThrow(new RecordNotFoundException("Record not found"));

            mockMvc.perform(put(ENDPOINT + "{id}", appSourceDetailsDto.getId())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(appSourceDetailsDto)))
                    .andExpect(status().isNotFound());
        }
    }
	
	@Nested
	@WithMockUser(username = "DashboardUser", authorities = { "DASHBOARD_ADMIN", "DASHBOARD_USER" })
	class Delete {
		@Test
		void testDelete() throws Exception {
	        mockMvc.perform(delete(ENDPOINT + "{id}", appSourceDetailsDto.getId())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OBJECT_MAPPER.writeValueAsString(appSourceDetailsDto)))
                    .andExpect(status().isOk());
	    }

		@Test
		void testRemoveAdmin() throws Exception {
			Mockito.when(service.removeAdminFromAppSource(Mockito.any(), Mockito.any())).thenReturn(appSourceDetailsDto);

			mockMvc.perform(delete(ENDPOINT + "admins/{id}", appSourceDetailsDto.getId())
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(OBJECT_MAPPER.writeValueAsString(DashboardUserDto.builder().email("joe@test.com").build())))
					.andExpect(status().isOk());
		}

		@Test
		void testRemoveAllAppClientPrivs() throws Exception {
			Mockito.when(service.deleteAllAppClientPrivs(Mockito.any())).thenReturn(appSourceDetailsDto);
			mockMvc.perform(delete(ENDPOINT + "app-clients/all/{appId}", appSourceDetailsDto.getId()))
					.andExpect(status().isOk());
		}

		@Test
		void testRemoveEndpointPriv() throws Exception {
			Mockito.when(service.removeEndPointPrivilege(Mockito.any(), Mockito.any())).thenReturn(appSourceDetailsDto);
			mockMvc.perform(delete(ENDPOINT + "app-clients/{appId}/{privId}", appSourceDetailsDto.getId(), UUID.randomUUID()))
					.andExpect(status().isOk());
		}
	}

	@Nested
	@WithMockUser(username = "DashboardUser", authorities = { "DASHBOARD_ADMIN", "DASHBOARD_USER" })
	class Patch {

		@Test
		void testAddAdmin() throws Exception {
			Mockito.when(service.addAppSourceAdmin(Mockito.any(), Mockito.any())).thenReturn(appSourceDetailsDto);

			mockMvc.perform(patch(ENDPOINT + "admins/{id}", appSourceDetailsDto.getId())
					.accept(MediaType.APPLICATION_JSON)
					.contentType(MediaType.APPLICATION_JSON)
					.content(OBJECT_MAPPER.writeValueAsString(DashboardUserDto.builder().email("joe@test.com").build())))
					.andExpect(status().isOk());
		}
	}
}

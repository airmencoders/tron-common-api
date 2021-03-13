package mil.tron.commonapi.service;

import lombok.val;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.entity.appsource.AppSourcePriv;
import mil.tron.commonapi.repository.appsource.AppSourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class AppSourceServiceImplTest {

    @Mock
    private AppSourceRepository appSourceRepository;

    @InjectMocks
    private AppSourceServiceImpl service;

    private static UUID APP_SOURCE_UUID = UUID.randomUUID();
    private static String APP_SOURCE_NAME = "Test App Source";
    private List<AppSource> entries = new ArrayList<>();
    private Set<AppSourcePriv> appSourcePrivs = new HashSet<>();
    private AppSource appSource;

    @BeforeEach
    void setup() {
        this.appSource = AppSource
                .builder()
                .id(APP_SOURCE_UUID)
                .name(APP_SOURCE_NAME)
                .build();
        val clientUser = AppClientUser
                .builder()
                .id(UUID.randomUUID())
                .name("Test App Client")
                .build();
        val appSourcePriv = AppSourcePriv
                .builder()
                .id(UUID.randomUUID())
                .appSource(appSource)
                .appClientUser(clientUser)
                .build();
        appSourcePrivs.add(
            appSourcePriv
        );
        appSource.setAppSourcePrivs(appSourcePrivs);
        clientUser.setAppSourcePrivs(appSourcePrivs);
        entries.add(appSource);
    }

    @Test
    void testGetAppSources() {
        Mockito.when(appSourceRepository.findAll()).thenReturn(entries);
        assertEquals(1, service.getAppSources().size());
    }

    @Test
    void testGetAppSourceDetails() {
        Mockito.when(appSourceRepository.findById(APP_SOURCE_UUID)).thenReturn(
                Optional.of(this.appSource));
        assertEquals(APP_SOURCE_NAME, service.getAppSource(APP_SOURCE_UUID).getName());
    }

}

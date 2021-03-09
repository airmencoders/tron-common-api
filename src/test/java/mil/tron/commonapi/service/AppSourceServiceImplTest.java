package mil.tron.commonapi.service;

import lombok.val;
import mil.tron.commonapi.dto.AppClientUserPrivDto;
import mil.tron.commonapi.dto.appsource.AppSourceDetailsDto;
import mil.tron.commonapi.dto.appsource.AppSourcePrivDto;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.entity.appsource.AppSourcePriv;
import mil.tron.commonapi.repository.appsource.AppSourcePrivRepository;
import mil.tron.commonapi.repository.appsource.AppSourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;

import javax.transaction.Transactional;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class AppSourceServiceImplTest {

    @Mock
    private AppSourceRepository appSourceRepository;

    @InjectMocks
    private AppSourceServiceImpl service;

    private List<AppSource> entries = new ArrayList<>();
    private Set<AppSourcePriv> appSourcePrivs = new HashSet<>();

    @BeforeEach
    void setup() {
        val appSource = AppSource
                .builder()
                .id(UUID.randomUUID())
                .name("Test App Source")
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

}

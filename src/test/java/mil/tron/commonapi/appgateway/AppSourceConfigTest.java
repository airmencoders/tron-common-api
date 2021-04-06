package mil.tron.commonapi.appgateway;

import mil.tron.commonapi.entity.appsource.AppSource;
import mil.tron.commonapi.repository.appsource.AppSourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class AppSourceConfigTest {

    @Mock
    private AppSourceRepository appSourceRepository;

    @Test
    void testRegisterAppSource() {
        AppSourceRepository spyRepo = Mockito.spy(this.appSourceRepository);
        AppSourceInterfaceDefinition appSourceDef = new AppSourceInterfaceDefinition("Name",
                "filename.yml", "http:////sourceurl", "/path");
        ArgumentCaptor<AppSource> captor = ArgumentCaptor.forClass(AppSource.class);
        AppSourceConfig appSourceConfig = new AppSourceConfig(
                spyRepo, "noDef"
        );
        appSourceConfig.registerAppSource(appSourceDef);
        verify(spyRepo).save(captor.capture());
        AppSource captorValue = captor.getValue();
        assertThat(captorValue.getName()).isEqualTo(appSourceDef.getName());
        assertThat(captorValue.getOpenApiSpecFilename()).isEqualTo(appSourceDef.getOpenApiSpecFilename());
    }
}

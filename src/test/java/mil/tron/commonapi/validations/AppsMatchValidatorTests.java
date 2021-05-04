package mil.tron.commonapi.validations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import javax.validation.ConstraintValidatorContext;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import mil.tron.commonapi.dto.appsource.AppEndPointPrivDto;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.appsource.AppEndpointPriv;
import mil.tron.commonapi.entity.appsource.AppSource;

@ExtendWith(SpringExtension.class)
public class AppsMatchValidatorTests {
    private AppsMatchValidator validator;
    private AppsMatch appsMatch = mock(AppsMatch.class);
    private ConstraintValidatorContext context = mock(ConstraintValidatorContext.class);
        
    @ParameterizedTest
    @ValueSource(booleans = { false, true})
    public void shouldReturnTrueWhenBothValuesAreNull(boolean inverse) {
        initialize(inverse);

        AppEndpointPriv priv = AppEndpointPriv.builder()
            .build();

        assertEquals(!inverse, validator.isValid(priv, context));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true})
    public void shouldReturnFalseWhenOnlyFirstValueIsNull(boolean inverse) {
        initialize(inverse);

        AppEndpointPriv priv = AppEndpointPriv.builder()
            .appSource(AppSource.builder().id(UUID.randomUUID()).build())
            .build();

        assertEquals(inverse, validator.isValid(priv, context));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true})
    public void shouldReturnFalseWhenOnlySecondValueIsNull(boolean inverse) {
        initialize(inverse);

        AppEndpointPriv priv = AppEndpointPriv.builder()
            .appClientUser(AppClientUser.builder().id(UUID.randomUUID()).build())
            .build();

        assertEquals(inverse, validator.isValid(priv, context));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true})
    public void shouldReturnTrueWhenBothValuesHaveSameId(boolean inverse) {
        initialize(inverse);

        UUID id = UUID.randomUUID();

        AppEndpointPriv priv = AppEndpointPriv.builder()
            .appSource(AppSource.builder().id(id).build())
            .appClientUser(AppClientUser.builder().id(id).build())
            .build();

        assertEquals(!inverse, validator.isValid(priv, context));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true})
    public void shouldReturnFalseWhenValuesHaveDifferentIds(boolean inverse) {
        initialize(inverse);

        AppEndpointPriv priv = AppEndpointPriv.builder()
            .appSource(AppSource.builder().id(UUID.randomUUID()).build())
            .appClientUser(AppClientUser.builder().id(UUID.randomUUID()).build())
            .build();

        assertEquals(inverse, validator.isValid(priv, context));
    }
    
    @ParameterizedTest
    @ValueSource(booleans = { false, true})
    public void shouldReturnFalseWhenFirstTypeIsAppAndSecondIsId(boolean inverse) {
        when(appsMatch.field()).thenReturn("appSource");
        when(appsMatch.fieldMatch()).thenReturn("id");
        when(appsMatch.invert()).thenReturn(inverse);

        validator = new AppsMatchValidator();
        validator.initialize(appsMatch);

        AppEndpointPriv priv = AppEndpointPriv.builder()
            .appSource(AppSource.builder().id(UUID.randomUUID()).build())
            .appClientUser(AppClientUser.builder().id(UUID.randomUUID()).build())
            .build();

        assertEquals(inverse, validator.isValid(priv, context));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true})
    public void shouldReturnFalseWhenFirstTypeIsIdAndSecondIsApp(boolean inverse) {
        when(appsMatch.field()).thenReturn("id");
        when(appsMatch.fieldMatch()).thenReturn("appClientUser");
        when(appsMatch.invert()).thenReturn(inverse);

        validator = new AppsMatchValidator();
        validator.initialize(appsMatch);

        AppEndpointPriv priv = AppEndpointPriv.builder()
            .appSource(AppSource.builder().id(UUID.randomUUID()).build())
            .appClientUser(AppClientUser.builder().id(UUID.randomUUID()).build())
            .build();

        assertEquals(inverse, validator.isValid(priv, context));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true})
    public void shouldReturnTrueWhenBothValuesAreSameId(boolean inverse) {
        when(appsMatch.field()).thenReturn("appSourceId");
        when(appsMatch.fieldMatch()).thenReturn("appClientUserId");
        when(appsMatch.invert()).thenReturn(inverse);

        validator = new AppsMatchValidator();
        validator.initialize(appsMatch);

        UUID id = UUID.randomUUID();

        AppEndPointPrivDto priv = AppEndPointPrivDto.builder()
            .appSourceId(id)
            .appClientUserId(id)
            .build();

        assertEquals(!inverse, validator.isValid(priv, context));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true})
    public void shouldReturnFalseWhenValuesAreDifferentIds(boolean inverse) {
        when(appsMatch.field()).thenReturn("appSourceId");
        when(appsMatch.fieldMatch()).thenReturn("appClientUserId");
        when(appsMatch.invert()).thenReturn(inverse);

        validator = new AppsMatchValidator();
        validator.initialize(appsMatch);

        AppEndPointPrivDto priv = AppEndPointPrivDto.builder()
            .appSourceId(UUID.randomUUID())
            .appClientUserId(UUID.randomUUID())
            .build();

        assertEquals(inverse, validator.isValid(priv, context));
    }  

    private void initialize(boolean inverse) {
        when(appsMatch.field()).thenReturn("appSource");
        when(appsMatch.fieldMatch()).thenReturn("appClientUser");
        when(appsMatch.invert()).thenReturn(inverse);

        validator = new AppsMatchValidator();
        validator.initialize(appsMatch);
    }
}

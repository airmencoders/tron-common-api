package mil.tron.commonapi.validations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import mil.tron.commonapi.dto.AppClientUserPrivDto;
import mil.tron.commonapi.dto.appsource.AppSourceDetailsDto;

public class AppAndIdMatchValidatorTests {
    private AppAndIdMatchValidator validator = new AppAndIdMatchValidator();

    @Test
    public void shouldReturnTrueWhenAppClientDoesNotMatchAppSourceId() {
        AppSourceDetailsDto dto = AppSourceDetailsDto.builder()
            .appClients(Arrays.asList(AppClientUserPrivDto.builder().id(UUID.randomUUID()).build()))
            .build();
        assertTrue(validator.isValid(dto, null));
    }

    @Test
    public void shouldReturnTrueWhenManyAppClientsDoNotMatchAppSourceId() {
        AppSourceDetailsDto dto = AppSourceDetailsDto.builder()
            .appClients(Arrays.asList(
                AppClientUserPrivDto.builder().appClientUser(UUID.randomUUID()).build(), 
                AppClientUserPrivDto.builder().appClientUser(UUID.randomUUID()).build(),
                AppClientUserPrivDto.builder().appClientUser(UUID.randomUUID()).build()))
            .build();
        assertTrue(validator.isValid(dto, null));
    }

    @Test
    public void shouldReturnTrueWhenAppClientsAreEmpty() {
        AppSourceDetailsDto dto = AppSourceDetailsDto.builder()
            .build();
        assertTrue(validator.isValid(dto, null));
    }

    @Test
    public void shouldReturnTrueWhenAppSourceIdIsNull() {
        AppSourceDetailsDto dto = AppSourceDetailsDto.builder()
            .id(null)
            .appClients(Arrays.asList(AppClientUserPrivDto.builder().appClientUser(UUID.randomUUID()).build()))
            .build();
        assertTrue(validator.isValid(dto, null));
    }

    @Test
    public void shouldReturnTrueIfClassIsNotAppSourceDetailsDto() {
        Object obj = new Object();
        assertTrue(validator.isValid(obj, null));
    }

    @Test
    public void shouldReturnFalseIfAnAppClientIdMatchesTheAppSourceId() {
        UUID id = UUID.randomUUID();
        AppSourceDetailsDto dto = AppSourceDetailsDto.builder()
            .id(id)
            .appClients(Arrays.asList(
                AppClientUserPrivDto.builder().appClientUser(UUID.randomUUID()).build(), 
                AppClientUserPrivDto.builder().appClientUser(id).build(),
                AppClientUserPrivDto.builder().appClientUser(UUID.randomUUID()).build()))
            .build();
        assertFalse(validator.isValid(dto, null));
    }
}

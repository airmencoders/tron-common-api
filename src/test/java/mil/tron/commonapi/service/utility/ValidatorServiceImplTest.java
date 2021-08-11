package mil.tron.commonapi.service.utility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.MethodArgumentNotValidException;

import lombok.AllArgsConstructor;

@ExtendWith(value={SpringExtension.class})
class ValidatorServiceImplTest {
	@TestConfiguration
	static class ValidatorServiceTestConfig {
		@Bean
		public Validator validator() {
			return new LocalValidatorFactoryBean();
		}
	}
	
	@AllArgsConstructor
	static class TestDto {
		@Size(max = 255)
		@NotBlank
		private String name;
	}
	
	@Autowired
	private Validator validator;
	
	private ValidatorService validatorService;
	
	@BeforeEach
	void setup() {
		validatorService = new ValidatorServiceImpl(validator);
	}
	
	@Test
	void isValid_shouldReturnTrue_whenNoValidationViolations() throws MethodArgumentNotValidException {
		TestDto testDto = new TestDto("Test Dto");
		
		assertThat(validatorService.isValid(testDto, TestDto.class)).isTrue();
	}
	
	@Test
	void isValid_shouldThrow_whenValidationViolationsExist() throws MethodArgumentNotValidException {
		TestDto testDto = new TestDto(StringUtils.repeat('D', 256));
		
		assertThrows(MethodArgumentNotValidException.class, () -> validatorService.isValid(testDto, TestDto.class));
	}
}

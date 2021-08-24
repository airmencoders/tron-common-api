package mil.tron.commonapi.service.utility;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpLogsUtilServiceImplTest {
	
	HttpLogsUtilServiceImpl httplogsUtilService;
	
	@BeforeEach
	void setup() {
		httplogsUtilService = new HttpLogsUtilServiceImpl();
	}
	
	@Test
	void getDateAtStartOfDay_shouldReturnDateAtStartOfDay() {
		 // Tuesday, August 24, 2021 4:00:00
		Date testDate = new Date(1629777600000L);
		
		// Tuesday, August 24, 2021 0:00:00
		Date testDateAtStartOfDay = new Date(1629763200000L);
		
		assertThat(httplogsUtilService.getDateAtStartOfDay(testDate)).isEqualTo(testDateAtStartOfDay);
	}
	
	@Test
	void getDateAtEndOfDay_shouldReturnDateAtEndOfDay() {
		 // Tuesday, August 24, 2021 4:00:00
		Date testDate = new Date(1629777600000L);
		
		// Tuesday, August 24, 2021 23:59:59
		Date testDateAtEndOfDay = new Date(1629849599000L);
		
		assertThat(httplogsUtilService.getDateAtEndOfDay(testDate)).isEqualTo(testDateAtEndOfDay);
	}
	
	@Test
	void isUsernameAnAppClient_shouldReturnFalse_whenNameIsEmail() {
		assertThat(httplogsUtilService.isUsernameAnAppClient("test@email.com")).isFalse();
	}
	
	@Test
	void isUsernameAnAppClient_shouldReturnTrue_whenNameIsAppClient() {
		assertThat(httplogsUtilService.isUsernameAnAppClient("guardianangel")).isTrue();
	}
}

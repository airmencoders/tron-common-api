package mil.tron.commonapi.service.utility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import mil.tron.commonapi.exception.BadRequestException;

class HttpLogsUtilServiceImplTest {
	private Clock systemUtcClock;
	private HttpLogsUtilServiceImpl httplogsUtilService;
	
	@BeforeEach
	void setup() {
		systemUtcClock = Mockito.mock(Clock.class);
		httplogsUtilService = new HttpLogsUtilServiceImpl(systemUtcClock);
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
	
	@Test
	void isDateBeforeOrEqualTo_shouldReturnTrue_whenDateBefore() {
		// Sunday, August 29, 2021 0:00:00
		Date startDate = new Date(1630195200000L);
		
		// Sunday, August 29, 2021 1:00:00
		Date endDate = new Date(1630198800000L);
		
		assertThat(httplogsUtilService.isDateBeforeOrEqualTo(startDate, endDate, true)).isTrue();
	}
	
	@Test
	void isDateBeforeOrEqualTo_shouldReturnTrue_whenDateEqual() {
		// Sunday, August 29, 2021 0:00:00
		Date startDate = new Date(1630195200000L);
		
		// Sunday, August 29, 2021 0:00:00
		Date endDate = new Date(1630195200000L);
		
		assertThat(httplogsUtilService.isDateBeforeOrEqualTo(startDate, endDate, true)).isTrue();
	}
	
	@Test
	void isDateBeforeOrEqualTo_shouldReturnFalse_whenDateAfter() {
		// Sunday, August 29, 2021 1:00:00
		Date startDate = new Date(1630198800000L);
		
		// Sunday, August 29, 2021 0:00:00
		Date endDate = new Date(1630195200000L);
		
		assertThat(httplogsUtilService.isDateBeforeOrEqualTo(startDate, endDate, false)).isFalse();
	}
	
	@Test
	void isDateBeforeOrEqualTo_shouldThrow_whenDateAfter() {
		// Sunday, August 29, 2021 1:00:00
		Date startDate = new Date(1630198800000L);
		
		// Sunday, August 29, 2021 0:00:00
		Date endDate = new Date(1630195200000L);
		
		assertThatThrownBy(() -> {
			httplogsUtilService.isDateBeforeOrEqualTo(startDate, endDate, true);
		})
			.isInstanceOf(BadRequestException.class)
			.hasMessage("Start Date is after End Date");
	}
	
	@Test
	void isDateInThePast_shouldReturnTrue_whenDateBeforeReference() {
		// Sunday, August 29, 2021 0:00:00
		Date startDate = new Date(1630195200000L);
		
		// Sunday, August 29, 2021 1:00:00
		Date referenceDate =new Date(1630198800000L);
		
		assertThat(httplogsUtilService.isDateInThePast(startDate, referenceDate, true)).isTrue();
	}
	
	@Test
	void isDateInThePast_shouldReturnTrue_whenDateBeforeAndNoReference() {
		// Sunday, August 29, 2021 0:00:00
		Date startDate = new Date(1630195200000L);
		
		// Sunday, August 29, 2021 1:00:00
		Instant currentDate = Instant.ofEpochMilli(1630198800000L);
		
		Clock fixedClock = Clock.fixed(currentDate, ZoneId.of("UTC"));
		Mockito.when(systemUtcClock.instant()).thenReturn(fixedClock.instant());
		
		assertThat(httplogsUtilService.isDateInThePast(startDate, null, true)).isTrue();
	}
	
	@Test
	void isDateInThePast_shouldReturnFalse_whenDateAfterReference() {
		// Sunday, August 29, 2021 1:00:00
		Date startDate =new Date(1630198800000L);
		
		// Sunday, August 29, 2021 0:00:00
		Date referenceDate = new Date(1630195200000L);
		
		assertThat(httplogsUtilService.isDateInThePast(startDate, referenceDate, false)).isFalse();
	}
	
	@Test
	void isDateInThePast_shouldReturnFalse_whenDateAfterAndNoReference() {
		// Sunday, August 29, 2021 1:00:00
		Date startDate = new Date(1630198800000L);
		
		// Sunday, August 29, 2021 0:00:00
		Instant currentDate = Instant.ofEpochMilli(1630195200000L);
		
		Clock fixedClock = Clock.fixed(currentDate, ZoneId.of("UTC"));
		Mockito.when(systemUtcClock.instant()).thenReturn(fixedClock.instant());
		
		assertThat(httplogsUtilService.isDateInThePast(startDate, null, false)).isFalse();
	}
	
	@Test
	void isDateInThePast_shouldThrow_whenDateAfterAndNoReferenceAndThrowOnFailure() {
		// Sunday, August 29, 2021 1:00:00
		Date startDate = new Date(1630198800000L);
		
		// Sunday, August 29, 2021 0:00:00
		Instant currentDate = Instant.ofEpochMilli(1630195200000L);
		
		Clock fixedClock = Clock.fixed(currentDate, ZoneId.of("UTC"));
		Mockito.when(systemUtcClock.instant()).thenReturn(fixedClock.instant());
		
		assertThatThrownBy(() -> {
			httplogsUtilService.isDateInThePast(startDate, null, true);
		})
			.isInstanceOf(BadRequestException.class)
			.hasMessage("Date is in the future");
	}
	
	@Test
	void isDateInThePast_shouldReturnFalse_whenDateEqualsReference() {
		// Sunday, August 29, 2021 1:00:00
		Date startDate =new Date(1630198800000L);
		
		// Sunday, August 29, 2021 1:00:00
		Date referenceDate = new Date(1630198800000L);
		
		assertThat(httplogsUtilService.isDateInThePast(startDate, referenceDate, false)).isFalse();
	}
	
	@Test
	void isDateInThePast_shouldThrow_whenDateEqualsReferenceAndThrowOnFailure() {
		// Sunday, August 29, 2021 1:00:00
		Date startDate =new Date(1630198800000L);
		
		// Sunday, August 29, 2021 1:00:00
		Date referenceDate = new Date(1630198800000L);
		
		assertThatThrownBy(() -> {
			httplogsUtilService.isDateInThePast(startDate, referenceDate, true);
		})
			.isInstanceOf(BadRequestException.class)
			.hasMessage("Date is in the future");
	}
}

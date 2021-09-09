package mil.tron.commonapi.service.utility;

import java.time.Clock;
import java.time.Instant;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import mil.tron.commonapi.exception.BadRequestException;

@Service
public class HttpLogsUtilServiceImpl implements HttpLogsUtilService {
	private final Pattern emailDomainPattern = Pattern.compile("[@].+[.].+");
	private final Clock systemUtcClock;
	
	public HttpLogsUtilServiceImpl(Clock systemUtcClock) {
		this.systemUtcClock = systemUtcClock;
	}

	@Override
	public Date getDateAtStartOfDay(Date date) {
		Calendar now = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		now.setTime(date);
		now.set(Calendar.HOUR_OF_DAY, 0);
		now.set(Calendar.MINUTE, 0);
		now.set(Calendar.SECOND, 0);
		return now.getTime();
	}

	@Override
	public Date getDateAtEndOfDay(Date date) {
		Calendar now = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		now.setTime(date);
		now.set(Calendar.HOUR_OF_DAY, 23);
		now.set(Calendar.MINUTE, 59);
		now.set(Calendar.SECOND, 59);
		return now.getTime();
	}

	@Override
	public boolean isUsernameAnAppClient(String username) {
		Matcher emailMatcher = emailDomainPattern.matcher(username);
		
		return !emailMatcher.find();
	}

	@Override
	public boolean isDateBeforeOrEqualTo(Date startDate, Date endDate, boolean throwOnFailure) throws BadRequestException {
		if (startDate.compareTo(endDate) > 0) {
			
			if (throwOnFailure) {
				throw new BadRequestException("Start Date is after End Date");
			}
            
			return false;
        }
		
		return true;
	}

	@Override
	public boolean isDateInThePast(Date date, @Nullable Date referenceDate, boolean throwOnFailure) throws BadRequestException {
		if (referenceDate == null) {
			referenceDate = Date.from(Instant.now(systemUtcClock));
		}
		
		if (date.before(referenceDate)) {
			return true;
		}
		
		if (throwOnFailure) {
			throw new BadRequestException("Date is in the future");
		}
		
		return false;
	}
}

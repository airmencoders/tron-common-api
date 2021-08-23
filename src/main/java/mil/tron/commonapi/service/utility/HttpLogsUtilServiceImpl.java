package mil.tron.commonapi.service.utility;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

@Service
public class HttpLogsUtilServiceImpl implements HttpLogsUtilService {
	private final Pattern emailDomainPattern = Pattern.compile("[@].+[.].+");

	@Override
	public Pattern getEmailDomainPattern() {
		return emailDomainPattern;
	}

	@Override
	public Date getDateAtStartOfDay(Date date) {
		Calendar now = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		now.setTime(date);
		now.set(Calendar.HOUR_OF_DAY, 23);
		now.set(Calendar.MINUTE, 59);
		now.set(Calendar.SECOND, 59);
		return now.getTime();
	}

	@Override
	public Date getDateAtEndOfDay(Date date) {
		Calendar now = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		now.setTime(date);
		now.set(Calendar.HOUR_OF_DAY, 0);
		now.set(Calendar.MINUTE, 0);
		now.set(Calendar.SECOND, 0);
		return now.getTime();
	}

}

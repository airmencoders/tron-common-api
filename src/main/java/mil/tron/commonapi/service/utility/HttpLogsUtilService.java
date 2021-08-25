package mil.tron.commonapi.service.utility;

import java.util.Date;

public interface HttpLogsUtilService {
	/**
	 * Takes a Date and sets it to be at the end of day
	 * 
	 * @param date the date to get end of day
	 * @return date with hour, minutes, seconds set to 23:59:59
	 */
	Date getDateAtStartOfDay(Date date);
	
	/**
	 * Takes a date and sets it to be at the start of day
	 * @param date the date to get start of day
	 * @return date with hour, minutes, seconds set to 00:00:00
	 */
	Date getDateAtEndOfDay(Date date);
	
	/**
	 * Validates if {@code username} is LIKE an email address.
	 * @param username the name to check
	 * @return true if {@code username} does not resemble an email address
	 */
	boolean isUsernameAnAppClient(String username);
}

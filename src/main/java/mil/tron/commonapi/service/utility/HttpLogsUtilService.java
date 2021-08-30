package mil.tron.commonapi.service.utility;

import java.util.Date;


import mil.tron.commonapi.exception.BadRequestException;

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
	
	/**
	 * Checks if {@code startDate} is before or equal to {@code endDate}.
	 * @param startDate the first date
	 * @param endDate the second date
	 * @param throwOnFailure throws exception if true
	 * @return true if {@code startDate} is before or equals to endDate, false otherwise
	 * @throws BadRequestException throws if {@code startDate} is after endDate and {@code throwOnFailure} is true
	 */
	boolean isDateBeforeOrEqualTo(Date startDate, Date endDate, boolean throwOnFailure) throws BadRequestException;
	
	
	/**
	 * Compares {@code date} to check if it is in the past ({@code date} > {@code referenceDate}).
	 * @param date the date to compare
	 * @param referenceDate the date to compare against. If null, a date will be generate at the current {@link java.time.Instant#now(java.time.Clock)} in UTC.
	 * @param throwOnFailure true to throw exception on failure
	 * @return true if {@code date} is in the past, false otherwise
	 * @throws BadRequestException throws if {@code throwOnFailure} is true and the check has failed
	 */
	boolean isDateInThePast(Date date, Date referenceDate, boolean throwOnFailure) throws BadRequestException;
}

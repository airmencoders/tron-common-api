package mil.tron.commonapi.repository.filter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.powermock.reflect.Whitebox;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import mil.tron.commonapi.dto.PrivilegeDto;
import mil.tron.commonapi.dto.appclient.AppClientUserDto;
import mil.tron.commonapi.dto.pubsub.SubscriberDto;
import mil.tron.commonapi.entity.AppClientUser;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.pubsub.events.EventType;
import mil.tron.commonapi.entity.pubsub.log.EventRequestLog;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.pubsub.messages.PubSubMessage;
import mil.tron.commonapi.repository.AppClientUserRespository;
import mil.tron.commonapi.repository.pubsub.SubscriberRepository;
import mil.tron.commonapi.repository.pubsub.log.EventRequestLogRepository;
import mil.tron.commonapi.service.AppClientUserServiceImpl;
import mil.tron.commonapi.service.PrivilegeServiceImpl;
import mil.tron.commonapi.service.pubsub.SubscriberServiceImpl;
import mil.tron.commonapi.service.pubsub.log.EventRequestServiceImpl;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
class SpecificationBuilderTest {
	@Nested
	class CastingToTypeTest {
		@Test
		void shouldReturnDouble_whenTypeIsDouble()
				throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			Method method = Whitebox.getMethod(SpecificationBuilder.class, "castToRequiredType", Class.class,
					String.class, String.class);
			Object value = method.invoke(null, Double.class, "fieldName", "0.18");

			assertThat(value)
				.isOfAnyClassIn(Double.class)
				.isEqualTo(0.18);
		}

		@Test
		void shouldReturnInteger_whenTypeIsInteger()
				throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			Method method = Whitebox.getMethod(SpecificationBuilder.class, "castToRequiredType", Class.class,
					String.class, String.class);
			Object value = method.invoke(null, Integer.class, "fieldName", "1");

			assertThat(value)
				.isOfAnyClassIn(Integer.class)
				.isEqualTo(1);
		}

		@Test
		void shouldReturnLong_whenTypeIsLong()
				throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			Method method = Whitebox.getMethod(SpecificationBuilder.class, "castToRequiredType", Class.class,
					String.class, String.class);
			Object value = method.invoke(null, long.class, "fieldName", "1");

			assertThat(value)
				.isOfAnyClassIn(Long.class)
				.isEqualTo(1L);
		}

		@Test
		void shouldReturnEnum_whenTypeIsEnum()
				throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			Method method = Whitebox.getMethod(SpecificationBuilder.class, "castToRequiredType", Class.class,
					String.class, String.class);
			Object value = method.invoke(null, Branch.class, "fieldName", "USA");

			assertThat(value)
				.isOfAnyClassIn(Branch.class)
				.isEqualTo(Branch.USA);
		}

		@Test
		void shouldReturnUUID_whenTypeIsUUID()
				throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			UUID id = UUID.randomUUID();
			Method method = Whitebox.getMethod(SpecificationBuilder.class, "castToRequiredType", Class.class,
					String.class, String.class);
			Object value = method.invoke(null, UUID.class, "fieldName", id.toString());

			assertThat(value)
				.isOfAnyClassIn(UUID.class)
				.isEqualTo(id);
		}

		@Test
		void shouldReturnDate_whenTypeIsDate()
				throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			String stringDate = "2021-09-28T08:29:37Z";
			Date date = new Date(1632817777000L);
			Method method = Whitebox.getMethod(SpecificationBuilder.class, "castToRequiredType", Class.class,
					String.class, String.class);
			Object value = method.invoke(null, Date.class, "fieldName", stringDate);

			assertThat(value)
				.isOfAnyClassIn(Date.class)
				.isEqualTo(date);
		}

		@Test
		void shouldReturnBoolean_whenTypeIsBoolean()
				throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			Method method = Whitebox.getMethod(SpecificationBuilder.class, "castToRequiredType", Class.class,
					String.class, String.class);
			Object value = method.invoke(null, Boolean.class, "fieldName", "true");

			assertThat(value)
				.isOfAnyClassIn(Boolean.class)
				.isEqualTo(true);

			value = method.invoke(null, Boolean.class, "fieldName", "false");
			assertThat(value)
				.isOfAnyClassIn(Boolean.class)
				.isEqualTo(false);
		}

		@Test
		void shouldReturnString_whenTypeDoesNotMatch()
				throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			Method method = Whitebox.getMethod(SpecificationBuilder.class, "castToRequiredType", Class.class,
					String.class, String.class);
			Object value = method.invoke(null, Boolean.class, "fieldName", "string value");

			assertThat(value)
				.isOfAnyClassIn(String.class)
				.isEqualTo("string value");
		}

		@Test
		void shouldThrow_whenGivenBadEnum() throws IllegalAccessException, IllegalArgumentException {
			Method method = Whitebox.getMethod(SpecificationBuilder.class, "castToRequiredType", Class.class,
					String.class, String.class);

			try {
				method.invoke(null, Branch.class, "fieldName", "DOESNOTEXIST");
			} catch (InvocationTargetException ex) {
				assertThat(ex.getCause()).isOfAnyClassIn(BadRequestException.class);
			}
		}

		@Test
		void shouldReturnListOfIntegers_whenTypeIsInteger()
				throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			List<String> values = List.of("1", "2", "3");
			Method method = Whitebox.getMethod(SpecificationBuilder.class, "castToRequiredType", Class.class,
					String.class, List.class);
			Object value = method.invoke(null, Integer.class, "fieldName", values);

			assertThat(value)
			.isOfAnyClassIn(ArrayList.class)
			.usingRecursiveComparison().asList().containsExactlyInAnyOrder(1, 2, 3);
		}
	}

	@Nested
	class ValidateOperatorSupportsInputTest {
		private Method validateOperatorSupportsInputTypeMethod;
		private Method checkOperatorSupportsInput;

		@BeforeEach
		void setup() {
			validateOperatorSupportsInputTypeMethod = Whitebox.getMethod(SpecificationBuilder.class,
					"validateOperatorSupportsInputType", QueryOperator.class, Class.class);
			checkOperatorSupportsInput = Whitebox.getMethod(SpecificationBuilder.class, "checkOperatorSupportsInput",
					QueryOperator.class, Class.class, String.class);
		}

		@Test
		void shouldReturnTrue_whenOperatorIsGreaterThanOrLessThan_andInputIsNumber()
				throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			boolean value = (boolean) validateOperatorSupportsInputTypeMethod.invoke(null, QueryOperator.GREATER_THAN,
					Integer.class);
			assertThat(value).isTrue();

			value = (boolean) validateOperatorSupportsInputTypeMethod.invoke(null, QueryOperator.LESS_THAN,
					Integer.class);
			assertThat(value).isTrue();
		}

		@Test
		void shouldReturnTrue_whenOperatorIsGreaterThanOrLessThan_andInputIsLong()
				throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			boolean value = (boolean) validateOperatorSupportsInputTypeMethod.invoke(null, QueryOperator.GREATER_THAN,
					long.class);
			assertThat(value).isTrue();

			value = (boolean) validateOperatorSupportsInputTypeMethod.invoke(null, QueryOperator.LESS_THAN, long.class);
			assertThat(value).isTrue();
		}

		@Test
		void shouldReturnTrue_whenOperatorIsGreaterThanOrLessThan_andInputIsDate()
				throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			boolean value = (boolean) validateOperatorSupportsInputTypeMethod.invoke(null, QueryOperator.GREATER_THAN,
					Date.class);
			assertThat(value).isTrue();

			value = (boolean) validateOperatorSupportsInputTypeMethod.invoke(null, QueryOperator.LESS_THAN, Date.class);
			assertThat(value).isTrue();
		}

		@Test
		void shouldReturnTrue_whenOperatorIsLikeOrNotLikeOrStartsWithOrEndsWith_andInputIsString()
				throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			boolean value = (boolean) validateOperatorSupportsInputTypeMethod.invoke(null, QueryOperator.LIKE,
					String.class);
			assertThat(value).isTrue();

			value = (boolean) validateOperatorSupportsInputTypeMethod.invoke(null, QueryOperator.NOT_LIKE,
					String.class);
			assertThat(value).isTrue();

			value = (boolean) validateOperatorSupportsInputTypeMethod.invoke(null, QueryOperator.STARTS_WITH,
					String.class);
			assertThat(value).isTrue();

			value = (boolean) validateOperatorSupportsInputTypeMethod.invoke(null, QueryOperator.ENDS_WITH,
					String.class);
			assertThat(value).isTrue();
		}

		@Test
		void shouldReturnTrue_whenOperatorHasNoConstraints_andInputIsDate()
				throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			boolean value = (boolean) validateOperatorSupportsInputTypeMethod.invoke(null, QueryOperator.EQUALS,
					UUID.class);
			assertThat(value).isTrue();
		}

		@Test
		void checkOperatorSupportsInput_shouldThrow_whenOperatorIsNotSupportedForInput()
				throws IllegalAccessException, IllegalArgumentException {
			try {
				checkOperatorSupportsInput.invoke(null, QueryOperator.GREATER_THAN, String.class, "fieldName");
			} catch (InvocationTargetException ex) {
				assertThat(ex.getCause()).isOfAnyClassIn(BadRequestException.class);
			}
		}
	}

	@Nested
	@Transactional
	class FilterTest {
		// Wednesday, July 21, 2021 0:00:00
		private final Instant FIXED_INSTANT = Instant.ofEpochMilli(1626825600000L);
		
		@Mock
		private Clock systemUtcClock;

		private EventRequestServiceImpl eventRequestLogService;

		@Autowired
		private AppClientUserServiceImpl appClientUserService;

		@Autowired
		private SubscriberServiceImpl subscriberService;
		
		@Autowired
		private PrivilegeServiceImpl privilegeService;

		@Autowired
		private SubscriberRepository subscriberRepo;

		@Autowired
		private EventRequestLogRepository eventRequestLogRepo;

		@Autowired
		private AppClientUserRespository appClientUserRep;
		
		private AppClientUserDto appClientDto1;
		private AppClientUserDto appClientDto2;
		
		@BeforeEach
		void setup() {
			eventRequestLogService = new EventRequestServiceImpl(eventRequestLogRepo, systemUtcClock);
			
			List<PrivilegeDto> privileges = StreamSupport.stream(privilegeService.getPrivileges().spliterator(), false).collect(Collectors.toList());
			PrivilegeDto organizationRead = privileges.stream().filter(i -> i.getName().equalsIgnoreCase("ORGANIZATION_READ")).findFirst().get();
			PrivilegeDto organizationDelete = privileges.stream().filter(i -> i.getName().equalsIgnoreCase("ORGANIZATION_DELETE")).findFirst().get();
			
			appClientDto1 = AppClientUserDto.builder().id(UUID.randomUUID()).name("specification app client 1")
					.privileges(Arrays.asList(organizationRead)).build();

			appClientDto2 = AppClientUserDto.builder().id(UUID.randomUUID()).name("specification app client 2")
					.privileges(Arrays.asList(organizationDelete, organizationRead)).build();

			appClientDto1 = appClientUserService.createAppClientUser(appClientDto1);
			appClientDto2 = appClientUserService.createAppClientUser(appClientDto2);

			SubscriberDto sub1 = SubscriberDto.builder().appClientUser(appClientDto1.getName())
					.secret(appClientDto1.getName()).subscribedEvent(EventType.ORGANIZATION_CHANGE)
					.subscriberAddress("/1/").id(UUID.randomUUID()).build();

			SubscriberDto sub2 = SubscriberDto.builder().appClientUser(appClientDto2.getName())
					.secret(appClientDto2.getName()).subscribedEvent(EventType.ORGANIZATION_DELETE)
					.subscriberAddress("/2/").id(UUID.randomUUID()).build();

			sub1 = subscriberService.upsertSubscription(sub1);
			sub2 = subscriberService.upsertSubscription(sub2);

			// Fix all events to to a single date
			Clock fixedClock = Clock.fixed(FIXED_INSTANT, ZoneId.of("UTC"));
			Mockito.when(systemUtcClock.instant()).thenReturn(fixedClock.instant());

			eventRequestLogService.createAndSaveEventRequestLogEntry(subscriberRepo.findById(sub1.getId()).get(),
					PubSubMessage.builder().eventCount(1L).eventType(sub1.getSubscribedEvent()).build(), true,
					"SUCCESS");

			eventRequestLogService.createAndSaveEventRequestLogEntry(subscriberRepo.findById(sub1.getId()).get(),
					PubSubMessage.builder().eventCount(2L).eventType(sub1.getSubscribedEvent()).build(), false, "FAIL");

			eventRequestLogService.createAndSaveEventRequestLogEntry(subscriberRepo.findById(sub2.getId()).get(),
					PubSubMessage.builder().eventCount(1L).eventType(sub2.getSubscribedEvent()).build(), true,
					"SUCCESS");

			eventRequestLogService.createAndSaveEventRequestLogEntry(subscriberRepo.findById(sub2.getId()).get(),
					PubSubMessage.builder().eventCount(2L).eventType(sub2.getSubscribedEvent()).build(), false, "FAIL");
		}

		@AfterEach
		void cleanup() {
			subscriberRepo.deleteAll();
			eventRequestLogRepo.deleteAll();
			appClientUserRep.deleteAll();
		}
		
		@Test
		void testFilterWithOrRelation() {
			List<FilterCriteria> filter = Arrays.asList(
						FilterCriteria.builder()
							.relationType(RelationType.OR)
							.field(String.format("%s.%s", EventRequestLog.APP_CLIENT_USER_FIELD, AppClientUser.ID_FIELD))
							.conditions(List.of(
										FilterCondition.builder()
											.operator(QueryOperator.EQUALS)
											.value(appClientDto1.getId().toString())
											.build(),
										FilterCondition.builder()
											.operator(QueryOperator.EQUALS)
											.value(appClientDto2.getId().toString())
											.build()
									))
							.build()
					);
			Specification<EventRequestLog> specification = SpecificationBuilder.getSpecificationFromFilters(filter);
			var page = eventRequestLogRepo.findAll(specification, Pageable.ofSize(10));
			var items = page.getContent();
			assertThat(items)
				.hasSize(4)
				.allMatch(i -> i.getAppClientUser().getId().equals(appClientDto1.getId()) || i.getAppClientUser().getId().equals(appClientDto2.getId()));
		}

		@Test
		void testEqualsFilter() {
			List<FilterCriteria> filter = Arrays.asList(
						FilterCriteria.builder()
							.relationType(RelationType.AND)
							.field(String.format("%s.%s", EventRequestLog.APP_CLIENT_USER_FIELD, AppClientUser.ID_FIELD))
							.conditions(List.of(
										FilterCondition.builder()
											.operator(QueryOperator.EQUALS)
											.value(appClientDto1.getId().toString())
											.build()
									))
							.build()
					);
			Specification<EventRequestLog> specification = SpecificationBuilder.getSpecificationFromFilters(filter);
			var page = eventRequestLogRepo.findAll(specification, Pageable.ofSize(10));
			var items = page.getContent();
			assertThat(items)
				.hasSize(2)
				.allMatch(i -> i.getAppClientUser().getId().equals(appClientDto1.getId()));
		}
		
		@Test
		void testEqualsFilter_withStringValue() {
			List<FilterCriteria> filter = Arrays.asList(
						FilterCriteria.builder()
							.relationType(RelationType.AND)
							.field(String.format("%s.%s", EventRequestLog.APP_CLIENT_USER_FIELD, AppClientUser.NAME_FIELD))
							.conditions(List.of(
										FilterCondition.builder()
											.operator(QueryOperator.EQUALS)
											.value(appClientDto1.getName())
											.build()
									))
							.build()
					);
			Specification<EventRequestLog> specification = SpecificationBuilder.getSpecificationFromFilters(filter);
			var page = eventRequestLogRepo.findAll(specification, Pageable.ofSize(10));
			var items = page.getContent();
			assertThat(items)
				.hasSize(2)
				.allMatch(i -> i.getAppClientUser().getName().equalsIgnoreCase(appClientDto1.getName()));
		}
		
		@Test
		void testNotEqualsFilter() {
			List<FilterCriteria> filter = Arrays.asList(
						FilterCriteria.builder()
							.relationType(RelationType.AND)
							.field(String.format("%s.%s", EventRequestLog.APP_CLIENT_USER_FIELD, AppClientUser.ID_FIELD))
							.conditions(List.of(
										FilterCondition.builder()
											.operator(QueryOperator.NOT_EQUALS)
											.value(appClientDto1.getId().toString())
											.build()
									))
							.build()
					);
			Specification<EventRequestLog> specification = SpecificationBuilder.getSpecificationFromFilters(filter);
			var page = eventRequestLogRepo.findAll(specification, Pageable.ofSize(10));
			var items = page.getContent();
			assertThat(items)
				.hasSize(2)
				.allMatch(i -> i.getAppClientUser().getId().equals(appClientDto2.getId()));
		}
		
		@Test
		void testNotEqualsFilter_withStringValue() {
			List<FilterCriteria> filter = Arrays.asList(
						FilterCriteria.builder()
							.relationType(RelationType.AND)
							.field(String.format("%s.%s", EventRequestLog.APP_CLIENT_USER_FIELD, AppClientUser.NAME_FIELD))
							.conditions(List.of(
										FilterCondition.builder()
											.operator(QueryOperator.NOT_EQUALS)
											.value(appClientDto1.getName())
											.build()
									))
							.build()
					);
			Specification<EventRequestLog> specification = SpecificationBuilder.getSpecificationFromFilters(filter);
			var page = eventRequestLogRepo.findAll(specification, Pageable.ofSize(10));
			var items = page.getContent();
			assertThat(items)
				.hasSize(2)
				.allMatch(i -> i.getAppClientUser().getName().equalsIgnoreCase(appClientDto2.getName()));
		}
		
		@Test
		void testNotLikeFilter() {
			List<FilterCriteria> filter = Arrays.asList(
						FilterCriteria.builder()
							.relationType(RelationType.AND)
							.field(String.format("%s.%s", EventRequestLog.APP_CLIENT_USER_FIELD, AppClientUser.NAME_FIELD))
							.conditions(List.of(
										FilterCondition.builder()
											.operator(QueryOperator.NOT_LIKE)
											.value(appClientDto1.getName())
											.build()
									))
							.build()
					);
			Specification<EventRequestLog> specification = SpecificationBuilder.getSpecificationFromFilters(filter);
			var page = eventRequestLogRepo.findAll(specification, Pageable.ofSize(10));
			var items = page.getContent();
			assertThat(items)
				.hasSize(2)
				.allMatch(i -> i.getAppClientUser().getName().equalsIgnoreCase(appClientDto2.getName()));
		}
		
		@Test
		void testLikeFilter() {
			List<FilterCriteria> filter = Arrays.asList(
						FilterCriteria.builder()
							.relationType(RelationType.AND)
							.field("reason")
							.conditions(List.of(
										FilterCondition.builder()
											.operator(QueryOperator.LIKE)
											.value("SUCCESS")
											.build()
									))
							.build()
					);
			Specification<EventRequestLog> specification = SpecificationBuilder.getSpecificationFromFilters(filter);
			var page = eventRequestLogRepo.findAll(specification, Pageable.ofSize(10));
			var items = page.getContent();
			assertThat(items)
				.hasSize(2)
				.allMatch(i -> i.getReason().equalsIgnoreCase("SUCCESS"));
		}
		
		@Test
		void testStartsWithFilter() {
			List<FilterCriteria> filter = Arrays.asList(
						FilterCriteria.builder()
							.relationType(RelationType.AND)
							.field("reason")
							.conditions(List.of(
										FilterCondition.builder()
											.operator(QueryOperator.STARTS_WITH)
											.value("SUCC")
											.build()
									))
							.build()
					);
			Specification<EventRequestLog> specification = SpecificationBuilder.getSpecificationFromFilters(filter);
			var page = eventRequestLogRepo.findAll(specification, Pageable.ofSize(10));
			var items = page.getContent();
			assertThat(items)
				.hasSize(2)
				.allMatch(i -> i.getReason().equalsIgnoreCase("SUCCESS"));
		}
		
		@Test
		void testEndsWithFilter() {
			List<FilterCriteria> filter = Arrays.asList(
						FilterCriteria.builder()
							.relationType(RelationType.AND)
							.field("reason")
							.conditions(List.of(
										FilterCondition.builder()
											.operator(QueryOperator.ENDS_WITH)
											.value("AIL")
											.build()
									))
							.build()
					);
			Specification<EventRequestLog> specification = SpecificationBuilder.getSpecificationFromFilters(filter);
			var page = eventRequestLogRepo.findAll(specification, Pageable.ofSize(10));
			var items = page.getContent();
			assertThat(items)
				.hasSize(2)
				.allMatch(i -> i.getReason().equalsIgnoreCase("FAIL"));
		}
		
		@Test
		void testGreaterThanFilter() {
			List<FilterCriteria> filter = Arrays.asList(
						FilterCriteria.builder()
							.relationType(RelationType.AND)
							.field("eventCount")
							.conditions(List.of(
										FilterCondition.builder()
											.operator(QueryOperator.GREATER_THAN)
											.value("1")
											.build()
									))
							.build()
					);
			Specification<EventRequestLog> specification = SpecificationBuilder.getSpecificationFromFilters(filter);
			var page = eventRequestLogRepo.findAll(specification, Pageable.ofSize(10));
			var items = page.getContent();
			assertThat(items)
				.hasSize(2)
				.allMatch(i -> i.getEventCount() > 1L);
		}
		
		@Test
		void testGreaterThanFilter_withDateValue() {
			// Tuesday, July 20, 2021 0:00:00
			Date dateInPast = new Date(1626739200000L);
			List<FilterCriteria> filter = Arrays.asList(
						FilterCriteria.builder()
							.relationType(RelationType.AND)
							.field("lastAttempted")
							.conditions(List.of(
										FilterCondition.builder()
											.operator(QueryOperator.GREATER_THAN)
											.value("2021-07-20T00:00:00Z")
											.build()
									))
							.build()
					);
			
			// Test a date in the past
			Specification<EventRequestLog> specification = SpecificationBuilder.getSpecificationFromFilters(filter);
			var page = eventRequestLogRepo.findAll(specification, Pageable.ofSize(10));
			var items = page.getContent();
			assertThat(items)
				.hasSize(4)
				.allMatch(i -> i.getLastAttempted().compareTo(dateInPast) > 0);
			
			// Test a date in the future
			filter = Arrays.asList(
					FilterCriteria.builder()
					.relationType(RelationType.AND)
					.field("lastAttempted")
					.conditions(List.of(
								FilterCondition.builder()
									.operator(QueryOperator.GREATER_THAN)
									.value("2021-07-22T00:00:00Z")
									.build()
							))
					.build()
			);
			
			specification = SpecificationBuilder.getSpecificationFromFilters(filter);
			page = eventRequestLogRepo.findAll(specification, Pageable.ofSize(10));
			items = page.getContent();
			assertThat(items)
				.isEmpty();
		}
		
		@Test
		void testLessThanFilter() {
			List<FilterCriteria> filter = Arrays.asList(
						FilterCriteria.builder()
							.relationType(RelationType.AND)
							.field("eventCount")
							.conditions(List.of(
										FilterCondition.builder()
											.operator(QueryOperator.LESS_THAN)
											.value("2")
											.build()
									))
							.build()
					);
			Specification<EventRequestLog> specification = SpecificationBuilder.getSpecificationFromFilters(filter);
			var page = eventRequestLogRepo.findAll(specification, Pageable.ofSize(10));
			var items = page.getContent();
			assertThat(items)
				.hasSize(2)
				.allMatch(i -> i.getEventCount() < 2L);
		}
		
		@Test
		void testLessThanFilter_withDateValue() {
			List<FilterCriteria> filter = Arrays.asList(
						FilterCriteria.builder()
							.relationType(RelationType.AND)
							.field("lastAttempted")
							.conditions(List.of(
										FilterCondition.builder()
											.operator(QueryOperator.LESS_THAN)
											.value("2021-07-20T00:00:00Z")
											.build()
									))
							.build()
					);
			
			// Test a date in the past
			Specification<EventRequestLog> specification = SpecificationBuilder.getSpecificationFromFilters(filter);
			var page = eventRequestLogRepo.findAll(specification, Pageable.ofSize(10));
			var items = page.getContent();
			assertThat(items)
				.isEmpty();
				
			// Test a date in the future
			// Tuesday, July 20, 2021 0:00:00
			Date dateInFuture = new Date(1626912000000L);
			filter = Arrays.asList(
					FilterCriteria.builder()
					.relationType(RelationType.AND)
					.field("lastAttempted")
					.conditions(List.of(
								FilterCondition.builder()
									.operator(QueryOperator.LESS_THAN)
									.value("2021-07-22T00:00:00Z")
									.build()
							))
					.build()
			);
			
			specification = SpecificationBuilder.getSpecificationFromFilters(filter);
			page = eventRequestLogRepo.findAll(specification, Pageable.ofSize(10));
			items = page.getContent();
			assertThat(items)
				.hasSize(4)
				.allMatch(i -> i.getLastAttempted().compareTo(dateInFuture) < 0);
		}
		
		@Test
		void testInFilter() {
			List<FilterCriteria> filter = Arrays.asList(
						FilterCriteria.builder()
							.relationType(RelationType.AND)
							.field(String.format("%s.%s", EventRequestLog.APP_CLIENT_USER_FIELD, AppClientUser.NAME_FIELD))
							.conditions(List.of(
										FilterCondition.builder()
											.operator(QueryOperator.IN)
											.values(Arrays.asList(appClientDto1.getName(), "name does not exist", "not exists"))
											.build()
									))
							.build()
					);
			Specification<EventRequestLog> specification = SpecificationBuilder.getSpecificationFromFilters(filter);
			var page = eventRequestLogRepo.findAll(specification, Pageable.ofSize(10));
			var items = page.getContent();
			assertThat(items)
				.hasSize(2)
				.allMatch(i -> i.getAppClientUser().getName().equalsIgnoreCase(appClientDto1.getName()));
		}
		
		@Test
		void testWithJoinAttributeFilter() {
			List<FilterCriteria> filter = Arrays.asList(
						FilterCriteria.builder()
							.relationType(RelationType.AND)
							.field(String.format("%s", AppClientUser.NAME_FIELD))
							.conditions(List.of(
										FilterCondition.builder()
											.operator(QueryOperator.EQUALS)
											.value(appClientDto1.getName())
											.build()
									))
							.joinAttribute(EventRequestLog.APP_CLIENT_USER_FIELD)
							.build()
					);
			Specification<EventRequestLog> specification = SpecificationBuilder.getSpecificationFromFilters(filter);
			var page = eventRequestLogRepo.findAll(specification, Pageable.ofSize(10));
			var items = page.getContent();
			assertThat(items)
				.hasSize(2)
				.allMatch(i -> i.getAppClientUser().getName().equalsIgnoreCase(appClientDto1.getName()));
		}
		
		@Test
		void testWithJoinAttributeFilter_shouldThrow_onInvalidField() {
			assertThatThrownBy(() -> {
				List<FilterCriteria> filter = Arrays.asList(
						FilterCriteria.builder()
							.relationType(RelationType.AND)
							.field("invalid field")
							.conditions(List.of(
										FilterCondition.builder()
											.operator(QueryOperator.EQUALS)
											.value(appClientDto1.getName())
											.build()
									))
							.joinAttribute(EventRequestLog.APP_CLIENT_USER_FIELD)
							.build()
					);
				Specification<EventRequestLog> specification = SpecificationBuilder.getSpecificationFromFilters(filter);
				eventRequestLogRepo.findAll(specification, Pageable.ofSize(10));
			})
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining(String.format("Field [%s] with Join Attribute [%s] does not exist or is invalid", "invalid field", EventRequestLog.APP_CLIENT_USER_FIELD));
		}
		
		@Test
		void testFilter_shouldThrow_onInvalidField() {
			assertThatThrownBy(() -> {
				List<FilterCriteria> filter = Arrays.asList(
						FilterCriteria.builder()
							.relationType(RelationType.AND)
							.field("invalid field")
							.conditions(List.of(
										FilterCondition.builder()
											.operator(QueryOperator.EQUALS)
											.value(appClientDto1.getName())
											.build()
									))
							.build()
					);
				Specification<EventRequestLog> specification = SpecificationBuilder.getSpecificationFromFilters(filter);
				eventRequestLogRepo.findAll(specification, Pageable.ofSize(10));
			})
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining(String.format("Field [%s] does not exist", "invalid field"));
		}
		
		@Test
		void testFieldPathFilter_shouldThrow_onInvalidFieldPath() {
			assertThatThrownBy(() -> {
				List<FilterCriteria> filter = Arrays.asList(
						FilterCriteria.builder()
							.relationType(RelationType.AND)
							.field("field.path.not.exists")
							.conditions(List.of(
										FilterCondition.builder()
											.operator(QueryOperator.EQUALS)
											.value(appClientDto1.getName())
											.build()
									))
							.build()
					);
				Specification<EventRequestLog> specification = SpecificationBuilder.getSpecificationFromFilters(filter);
				eventRequestLogRepo.findAll(specification, Pageable.ofSize(10));
			})
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("path does not exist");
		}
		
		@Test
		void testFieldPathFilter_shouldThrow_onInvalidNestedPath() {
			assertThatThrownBy(() -> {
				List<FilterCriteria> filter = Arrays.asList(
						FilterCriteria.builder()
							.relationType(RelationType.AND)
							.field(String.format("%s.%s.invalidNestedProperty", EventRequestLog.APP_CLIENT_USER_FIELD, AppClientUser.NAME_FIELD))
							.conditions(List.of(
										FilterCondition.builder()
											.operator(QueryOperator.EQUALS)
											.value(appClientDto1.getName())
											.build()
									))
							.build()
					);
				Specification<EventRequestLog> specification = SpecificationBuilder.getSpecificationFromFilters(filter);
				eventRequestLogRepo.findAll(specification, Pageable.ofSize(10));
			})
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("nested property is invalid");
		}
	}
}

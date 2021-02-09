package mil.tron.commonapi.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppClientUserTest {
	AppClientUser user;
	
	@BeforeEach
	void setup() {
		user = new AppClientUser();
	}
	
	@Test
	void testName_notNull_sanitization() {
		user.setName(" testName ");
		user.sanitize();
		
		assertThat(user.getName()).isEqualTo("testName");
	}
	
	@Test
	void testName_null_sanitization() {
		user.setName(null);
		user.sanitize();
		
		assertThat(user.getName()).isNull();
	}
}

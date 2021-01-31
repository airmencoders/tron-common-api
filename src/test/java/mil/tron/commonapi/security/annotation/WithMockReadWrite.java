package mil.tron.commonapi.security.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.springframework.security.test.context.support.WithMockUser;

@Retention(RetentionPolicy.RUNTIME)
@WithMockUser(username = "RW_USER", authorities = { "READ", "WRITE" })
public @interface WithMockReadWrite {

}

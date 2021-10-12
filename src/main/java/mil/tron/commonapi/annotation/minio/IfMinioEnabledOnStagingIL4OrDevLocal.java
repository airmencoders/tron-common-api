package mil.tron.commonapi.annotation.minio;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotation to enable a bean ONLY if we're in:
 *   -dev profile with minio enabled in app properties (application-development.properties)
 *   OR
 *   - we're in staging AND in IL4 with minio enabled in the app properties (application-staging.properties)
 */
@Retention(RetentionPolicy.RUNTIME)
@ConditionalOnExpression("${minio.enabled} && ((('${spring.profiles.active}' == 'staging') && ('${enclave.level}' == 'IL4')) || ('${spring.profiles.active}' == 'development'  || ('${spring.profiles.active}' == 'local'))")
public @interface IfMinioEnabledOnStagingIL4OrDevLocal {
}

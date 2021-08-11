package mil.tron.commonapi.annotation.jsonpatch;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Simple annotation to mark a field that can't be changed (when compared to existing entity) in a JSON Patch
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface NonPatchableField {
}

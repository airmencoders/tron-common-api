package mil.tron.commonapi.service.utility;

import mil.tron.commonapi.annotation.jsonpatch.NonPatchableField;
import mil.tron.commonapi.exception.BadRequestException;
import mil.tron.commonapi.exception.InvalidRecordUpdateRequest;
import org.apache.commons.lang3.reflect.FieldUtils;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ReflectionUtils {
    private ReflectionUtils() {}

    public static Set<String> fields(Class<?> target){
        return Arrays.stream(target.getDeclaredFields()).map(Field::getName).collect(Collectors.toSet());
    }

    /**
     * Utility method to compare a source object's @NonPatchableField annotated fields to its modified version
     * to ensure that no changes were made to them, and throwing if there have been.  Best way to check if a JsonPatch
     * is allowed or not since validation of those cannot be done in the @Valid traditional sense
     * @param source the Source object that is the source of truth
     * @param patched the patched object whose NonPatchable fields to check against the source
     */
    public static void checkNonPatchableFieldsUntouched(Object source, Object patched) {

        // get all the @NonPatchableFields from the source class
        List<Field> fields = FieldUtils.getFieldsListWithAnnotation(source.getClass(), NonPatchableField.class);

        try {

            // check that these "untouchable" fields are in fact not changed from the source
            for (Field f : fields) {
                if (!Objects.equals(FieldUtils.readField(source, f.getName(), true),
                        (FieldUtils.readField(patched, f.getName(), true)))) {

                    throw new InvalidRecordUpdateRequest("Cannot JSON Patch the field " + f.getName());
                }
            }
        }
        catch (IllegalAccessException e) {
            throw new BadRequestException("Unknown exception occurred trying validate a JSON Patch operation");
        }
    }
}

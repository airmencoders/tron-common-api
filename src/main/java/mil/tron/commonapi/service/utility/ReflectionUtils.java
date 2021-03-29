package mil.tron.commonapi.service.utility;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class ReflectionUtils {
    private ReflectionUtils() {}

    public static Set<String> fields(Class<?> target){
        return Arrays.stream(target.getDeclaredFields()).map(Field::getName).collect(Collectors.toSet());
    }
}

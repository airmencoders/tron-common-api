package mil.tron.commonapi.dto.annotation.helper;
import lombok.Getter;
import lombok.Setter;

public abstract class JsonPatchAnnotated {
    @Getter
    @Setter
    private String op;
    @Getter
    @Setter
    private String path;
    @Getter
    @Setter
    private String[] value;
}

package mil.tron.commonapi.exception.custom;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
public class TronCommonAppError {

    @Getter
    @Setter
    private int status;

    @Getter
    @Setter
    private String reason;

    @Getter
    @Setter
    private String path;

    @Getter
    @Setter
    private String error;

    public static TronCommonAppError fromDefaultAttributeMap(Map<String, Object> defaultErrorAttributes) {

        return new TronCommonAppError(((Integer)defaultErrorAttributes.getOrDefault("status", "")),
                (String) defaultErrorAttributes.getOrDefault("message", ""),
                (String) defaultErrorAttributes.getOrDefault("path", ""),
                (String) defaultErrorAttributes.getOrDefault("error", ""));
    }

    public Map<String, Object> toAttributeMap() {
        return Map.of(
                "status", this.status,
                "reason", this.reason,
                "path", this.path,
                "error", this.error
        );
    }

}

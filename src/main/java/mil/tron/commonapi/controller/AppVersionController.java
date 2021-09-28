package mil.tron.commonapi.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.CommonApiApplication;
import mil.tron.commonapi.dto.AppVersionInfoDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"${api-prefix.v1}/version", "${api-prefix.v2}/version"})
public class AppVersionController {

    // get the version from the JAR Manifest (when application is running as a JAR)
    private final String VERSION = CommonApiApplication.class.getPackage().getImplementationVersion();

    @Value("${spring.profiles.active:UNKNOWN}")
    private String environment;

    @Operation(summary = "Retrieves current running application version, along with the enclave level and environment this instance is running in",
            description = "The version is the first 8-characters of the SHA-1 commit hash of the master branch that this version was compiled from")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = AppVersionInfoDto.class)))
    })
    @GetMapping("")
    public ResponseEntity<Object> getVersion() {

        String enclaveLevel;

        try {
            enclaveLevel = System.getenv("ENCLAVE_LEVEL");
        } catch (SecurityException | NullPointerException e) {
            enclaveLevel = "UNKNOWN";
        }

        return new ResponseEntity<>(AppVersionInfoDto
                .builder()
                .version(VERSION == null ? "Unknown" : VERSION)
                .enclave(enclaveLevel)
                .environment(environment)
                .build(), HttpStatus.OK);
    }
}

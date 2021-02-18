package mil.tron.commonapi.controller.ranks;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import mil.tron.commonapi.entity.branches.Branch;
import mil.tron.commonapi.entity.ranks.Rank;
import mil.tron.commonapi.exception.RecordNotFoundException;
import mil.tron.commonapi.service.ranks.RankService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("${api-prefix.v1}/rank")
public class RankController {
    private RankService rankService;

    public RankController(RankService rankService) {
        this.rankService = rankService;
    }


    @Operation(summary = "Retrieves all ranks", description = "Retrieves all ranks")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = Rank.class))))
    })
    @GetMapping
    public ResponseEntity<Iterable<Rank>> getRanks() {
        return new ResponseEntity<>(rankService.getRanks(), HttpStatus.OK);
    }

    @Operation(summary = "Retrieves all ranks for a particular branch", description = "Retrieves all ranks for a particular branch")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = Rank.class)))),
            @ApiResponse(responseCode = "404",
                    description = "Resource not found",
                    content = @Content),
    })
    @GetMapping(value = "/{branch}")
    public ResponseEntity<Iterable<Rank>> getRanks(@PathVariable("branch") String branch) {
        return new ResponseEntity<>(rankService.getRanks(convertBranch(branch)), HttpStatus.OK);
    }

    @Operation(summary = "Retrieves information for a particular rank", description = "Retrieves information for a particular rank")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Successful operation",
                    content = @Content(schema = @Schema(implementation = Rank.class))),
            @ApiResponse(responseCode = "404",
                    description = "Resource not found",
                    content = @Content),
    })
    @GetMapping(value = "/{branch}/{abbreviation}")
    public ResponseEntity<Rank> getRank(@PathVariable("branch") String branch, @PathVariable("abbreviation") String abbreviation) {
        return new ResponseEntity<>(rankService.getRank(abbreviation, convertBranch(branch)), HttpStatus.OK);
    }

    private Branch convertBranch(String branch) {
        try {
            return Branch.valueOf(branch.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new RecordNotFoundException("Unknown Branch Name");
        }
    }
}

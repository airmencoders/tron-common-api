package mil.tron.commonapi.controller.scratch;

import mil.tron.commonapi.entity.scratch.ScratchStorageEntry;
import mil.tron.commonapi.service.scratch.ScratchStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("${api-prefix.v1}/scratch")
public class ScratchStorageController {

    private ScratchStorageService scratchStorageService;

    public ScratchStorageController(ScratchStorageService scratchStorageService) {
        this.scratchStorageService = scratchStorageService;
    }

    @GetMapping("")
    public ResponseEntity<Object> getAllKeyValuePairs() {
        return new ResponseEntity<>(scratchStorageService.getAllEntries(), HttpStatus.OK);
    }

    @GetMapping("/{appId}")
    public ResponseEntity<Object> getAllKeyValuePairsForAppId(@PathVariable UUID appId) {
        return new ResponseEntity<>(scratchStorageService.getAllEntriesByApp(appId), HttpStatus.OK);
    }

    @GetMapping("/{appId}/{keyName}")
    public ResponseEntity<Object> getKeyValueByKeyName(@PathVariable UUID appId, @PathVariable String keyName) {
        return new ResponseEntity<>(scratchStorageService.getKeyValueEntryByAppId(appId, keyName), HttpStatus.OK);
    }

    @PostMapping("")
    public ResponseEntity<Object> setKeyValuePair(@Valid @RequestBody ScratchStorageEntry entry) {
        return new ResponseEntity<>(
                scratchStorageService.setKeyValuePair(entry.getAppId(), entry.getKey(), entry.getValue()), HttpStatus.OK);
    }

    @DeleteMapping("/{appId}/{key}")
    public ResponseEntity<Object> deleteKeyValuePair(@PathVariable UUID appId, @PathVariable String key) {
        return new ResponseEntity<>(scratchStorageService.deleteKeyValuePair(appId, key), HttpStatus.OK);
    }

    @DeleteMapping("/{appId}")
    public ResponseEntity<Object> deleteAllKeyValuePairsForAppId(@PathVariable UUID appId) {
        return new ResponseEntity<>(scratchStorageService.deleteAllKeyValuePairsForAppId(appId), HttpStatus.OK);
    }
}

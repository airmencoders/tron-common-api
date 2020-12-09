package mil.tron.commonapi.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import mil.tron.commonapi.airman.Airman;
import mil.tron.commonapi.service.AirmanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/airman")
public class AirmanController {

    @Autowired
    private AirmanService airmanService;

    @Operation(summary = "Retrieves all airmen", description = "Retrieves all airmen records")
    @GetMapping("")
    public ResponseEntity<Iterable<Airman>> getAllAirman() {
        return new ResponseEntity<>(airmanService.getAllAirman(), HttpStatus.OK);
    }

    @Operation(summary = "Retrieves a single airman by UUID", description = "Retrieves single airman record")
    @GetMapping("/{id}")
    public ResponseEntity<Airman> getAirman(@Parameter(description = "UUID of the airman record", required= true) @PathVariable UUID id) {
        Airman airman = airmanService.getAirman(id);
        return airman != null ? new ResponseEntity<>(airman, HttpStatus.OK) : new ResponseEntity(HttpStatus.NOT_FOUND);
    }

    @Operation(summary = "Adds a new airman", description = "Adds a new airman, ID field should be null")
    @PostMapping("")
    public ResponseEntity<Airman> addAirman(@Parameter(description = "Airman record to add", required = true) @RequestBody Airman airman) {
        return new ResponseEntity<>(airmanService.createAirman(airman), HttpStatus.CREATED);
    }

    @Operation(summary = "Updates an existing airman record", description = "Updates an existing airman")
    @PutMapping("/{id}")
    public ResponseEntity<Airman> updateAirman(@Parameter(description = "Airman record ID to update", required = true) @PathVariable UUID id,
            @Parameter(description = "Airman record data", required = true) @RequestBody Airman airman) {

        Airman updatedAirman = airmanService.updateAirman(id, airman);
        return updatedAirman != null ? new ResponseEntity<>(updatedAirman, HttpStatus.OK) : new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @Operation(summary = "Deletes an airman record", description = "Removes an airman record from the database")
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteAirman(@Parameter(description = "UUID id of the airman record", required = true) @PathVariable UUID id) {
        airmanService.removeAirman(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

}

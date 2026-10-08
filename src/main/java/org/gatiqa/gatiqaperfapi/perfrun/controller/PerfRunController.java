package org.gatiqa.gatiqaperfapi.perfrun.controller;

import jakarta.validation.Valid;
import org.gatiqa.gatiqaperfapi.perfrun.dto.PerfRunDetailResponse;
import org.gatiqa.gatiqaperfapi.perfrun.dto.PerfRunRequest;
import org.gatiqa.gatiqaperfapi.perfrun.dto.PerfRunSummaryResponse;
import org.gatiqa.gatiqaperfapi.perfrun.service.PerfTestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/perf-runs")
public class PerfRunController {

    private final PerfTestService service;

    public PerfRunController(PerfTestService service) {
        this.service = service;
    }

    /**
     * Queue a performance run for the selected functional tests.
     * Returns the run immediately (status QUEUED); poll {@code GET /{id}}
     * until status is COMPLETED or FAILED.
     */
    @PostMapping
    public ResponseEntity<PerfRunDetailResponse> create(@Valid @RequestBody PerfRunRequest request) {
        PerfRunDetailResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.accepted().location(location).body(created);
    }

    /** Recent runs, newest first (up to 50). */
    @GetMapping
    public List<PerfRunSummaryResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public PerfRunDetailResponse getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    /** Diagnostic: tool names exposed by the configured engine's MCP server. */
    @GetMapping("/tools")
    public List<String> tools(@RequestParam(required = false) String engine) {
        return service.engineTools(engine);
    }
}

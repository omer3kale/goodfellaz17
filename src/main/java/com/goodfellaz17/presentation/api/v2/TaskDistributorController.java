package com.goodfellaz17.presentation.api.v2;

import com.goodfellaz17.application.service.ReactiveStreamingService;
import com.goodfellaz17.domain.model.StreamResult;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/tasks")
public class TaskDistributorController {

    private final ReactiveStreamingService streamingService;

    public TaskDistributorController(ReactiveStreamingService streamingService) {
        this.streamingService = streamingService;
    }

    @PostMapping("/distribute")
    public Flux<StreamResult> distributeTasks(
            @RequestParam int totalStreams,
            @RequestParam String trackId) {

        // Roadmap requirement: Distribute tasks across workers
        // Since the service uses flatMap with concurrency 50, it already handles distribution
        // to the worker pool balanced by the k8s service or docker-compose DNS.

        return streamingService.executeStreamBatch(totalStreams, trackId);
    }
}

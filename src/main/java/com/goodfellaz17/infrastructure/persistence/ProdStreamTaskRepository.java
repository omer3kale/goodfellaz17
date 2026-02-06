package com.goodfellaz17.infrastructure.persistence;

import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.goodfellaz17.domain.model.StreamResult;
import com.goodfellaz17.domain.port.StreamTaskRepositoryPort;

import reactor.core.publisher.Mono;

/**
 * Production implementation of StreamTaskRepositoryPort.
 * Stores StreamResults in-memory for the reactive streaming pipeline.
 *
 * TODO Phase 2: Replace with R2DBC persistence after thesis demo.
 *
 * Migration path:
 * 1. Create stream_results table schema:
 *    <pre>
 *    CREATE TABLE stream_results (
 *      id BIGSERIAL PRIMARY KEY,
 *      proxy_id UUID NOT NULL,
 *      track_id VARCHAR(255) NOT NULL,
 *      status VARCHAR(50) NOT NULL,
 *      timestamp TIMESTAMP DEFAULT NOW(),
 *      duration_ms INT,
 *      INDEX idx_stream_results_track (track_id),
 *      INDEX idx_stream_results_timestamp (timestamp)
 *    );
 *    </pre>
 * 2. Create StreamResultEntity R2DBC entity with @Table("stream_results")
 * 3. Implement StreamResultRepository extends ReactiveCrudRepository
 * 4. Replace ConcurrentHashMap with repository.save()
 * 5. Add retention policy (7-day automatic cleanup via @Scheduled)
 *
 * Current behavior: Thread-safe in-memory storage with auto-increment IDs.
 * Data is lost on restart - acceptable for thesis demo streaming metrics.
 *
 * @see StreamTaskRepositoryPort for the port interface
 * @see StreamResult for the domain model
 */
@Component
@Profile("!test")
public class ProdStreamTaskRepository implements StreamTaskRepositoryPort {
    private static final Logger log = LoggerFactory.getLogger(ProdStreamTaskRepository.class);

    /** In-memory store - replaced with R2DBC in Phase 2 */
    private final ConcurrentHashMap<Long, StreamResult> store = new ConcurrentHashMap<>();

    /** Simple auto-increment counter - replaced with DB sequence in Phase 2 */
    private volatile long idCounter = 0;

    /**
     * Save a stream result to in-memory storage.
     * In Phase 2, this will persist to stream_results table via R2DBC.
     *
     * @param result the streaming result to save
     * @return Mono containing the saved result
     */
    @Override
    public Mono<StreamResult> save(StreamResult result) {
        long id = ++idCounter;
        store.put(id, result);
        log.debug("Thesis stream result saved: proxyId={}, trackId={}, status={}",
            result.proxyId(), result.trackId(), result.status());
        return Mono.just(result);
    }
}

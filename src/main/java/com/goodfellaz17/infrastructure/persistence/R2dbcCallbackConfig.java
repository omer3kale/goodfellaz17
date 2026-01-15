package com.goodfellaz17.infrastructure.persistence;

import com.goodfellaz17.domain.model.generated.ProxyMetricsEntity;
import com.goodfellaz17.domain.model.generated.ProxyNodeEntity;
import org.reactivestreams.Publisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.mapping.event.AfterConvertCallback;
import reactor.core.publisher.Mono;

/**
 * R2DBC callbacks to properly handle Persistable entities.
 * 
 * After loading from DB, entities need to be marked as "not new"
 * so that subsequent saves perform UPDATE instead of INSERT.
 */
@Configuration
public class R2dbcCallbackConfig {

    @Bean
    AfterConvertCallback<ProxyNodeEntity> proxyNodeAfterConvertCallback() {
        return (entity, table) -> {
            entity.markNotNew();
            return Mono.just(entity);
        };
    }

    @Bean
    AfterConvertCallback<ProxyMetricsEntity> proxyMetricsAfterConvertCallback() {
        return (entity, table) -> {
            entity.markNotNew();
            return Mono.just(entity);
        };
    }
}

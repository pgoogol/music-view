package com.pgoogol.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Włącza harmonogram Springa (M4.7). Jedynym zadaniem cyklicznym jest na razie
 * odświeżanie playlist ze Spotify (D35); joby wzbogacania startują wyłącznie
 * ręcznie przez {@code EnrichmentService} i nie mają z tym nic wspólnego
 * ({@code spring.batch.job.enabled=false}).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

}

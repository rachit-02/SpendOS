package com.spendos.common.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/** Uses the in-memory cache by default; set CACHE_TYPE=redis (with REDIS_ENABLED=true) for Redis. */
@Configuration
@EnableCaching
public class CacheConfig {
}

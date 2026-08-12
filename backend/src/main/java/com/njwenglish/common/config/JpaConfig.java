package com.njwenglish.common.config;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaConfig {

    /**
     * 기본 공급자는 LocalDateTime을 준다. 이 프로젝트의 created_at·updated_at은 전부
     * OffsetDateTime(TIMESTAMPTZ)이라 이 빈이 없으면 모든 INSERT가
     * "Cannot convert unsupported date type java.time.LocalDateTime"으로 죽는다.
     */
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now());
    }
}

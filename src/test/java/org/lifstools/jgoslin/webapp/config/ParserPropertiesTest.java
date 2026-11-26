package org.lifstools.jgoslin.webapp.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ParserPropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ParserProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void timeLimitBinds() {
        runner.withPropertyValues("goslin.parser.time-limit=1s", "goslin.parser.request-time-limit=90s",
                "goslin.parser.threads=3").run(context -> {
            ParserProperties properties = context.getBean(ParserProperties.class);
            assertThat(properties.timeLimit()).isEqualTo(Duration.ofSeconds(1));
            assertThat(properties.requestTimeLimit()).isEqualTo(Duration.ofSeconds(90));
            assertThat(properties.threads()).isEqualTo(3);
        });
    }

    @Test
    void defaults() {
        runner.run(context -> {
            ParserProperties properties = context.getBean(ParserProperties.class);
            assertThat(properties.timeLimit()).isEqualTo(Duration.ofSeconds(20));
            assertThat(properties.requestTimeLimit()).isEqualTo(Duration.ofSeconds(120));
            assertThat(properties.threads()).isEqualTo(Math.max(2, Runtime.getRuntime().availableProcessors()));
        });
    }

    @Test
    void zeroBindsAsNoLimit() {
        runner.withPropertyValues("goslin.parser.time-limit=0", "goslin.parser.request-time-limit=0").run(context -> {
            ParserProperties properties = context.getBean(ParserProperties.class);
            assertThat(properties.timeLimit()).isZero();
            assertThat(properties.requestTimeLimit()).isZero();
        });
    }

    @Test
    void applicationPropertiesSetTheDocumentedValues() throws Exception {
        java.util.Properties p = new java.util.Properties();
        try (var in = getClass().getResourceAsStream("/application.properties")) {
            p.load(in);
        }
        assertThat(p.getProperty("goslin.parser.time-limit")).isEqualTo("20s");
        assertThat(p.getProperty("goslin.parser.request-time-limit")).isEqualTo("120s");
    }
}

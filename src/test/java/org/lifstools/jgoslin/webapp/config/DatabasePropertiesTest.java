package org.lifstools.jgoslin.webapp.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.lifstools.jgoslin.webapp.domain.DatabaseCoverage.Database;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class DatabasePropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DatabaseProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void versionBinds() {
        runner.withPropertyValues("goslin.databases.lipid-maps-version=1 May 2026").run(context -> {
            DatabaseProperties properties = context.getBean(DatabaseProperties.class);
            assertThat(properties.versionOf(Database.LIPID_MAPS)).isEqualTo("1 May 2026");
        });
    }

    @Test
    void defaults() {
        runner.run(context -> {
            DatabaseProperties properties = context.getBean(DatabaseProperties.class);
            assertThat(properties.versionOf(Database.LIPID_MAPS)).isEqualTo("3 March 2023");
            assertThat(properties.versionOf(Database.SWISS_LIPIDS)).isEqualTo("early 2023");
            assertThat(properties.versionOf(Database.CHEBI)).isEqualTo("18 January 2024");
        });
    }
}

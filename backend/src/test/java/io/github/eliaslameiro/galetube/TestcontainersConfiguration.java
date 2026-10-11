package io.github.eliaslameiro.galetube;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

 private static final String ACCESS_KEY = "test-access-key";
private static final String SECRET_KEY = "test-secret-key";
private static final String BUCKET = "galetube-test";
private static final int S3_PORT = 8333;

    @Bean 
    @ServiceConnection 
    MySQLContainer mysqlContainer() {
        return new MySQLContainer(DockerImageName.parse("mysql:8.4"));
    }

    @Bean
    GenericContainer<?> seaweedfs() {
        return new GenericContainer<>(DockerImageName.parse("chrislusf/seaweedfs:4.48"))
            .withCommand("mini", "-dir=/data")
            .withEnv("AWS_ACCESS_KEY_ID", ACCESS_KEY)
            .withEnv("AWS_SECRET_ACCESS_KEY", SECRET_KEY)
            .withEnv("S3_BUCKET", BUCKET)
            .withExposedPorts(S3_PORT)
            .waitingFor(Wait.forLogMessage(".*created bucket " + BUCKET + ".*", 1));
    }

    @Bean
    DynamicPropertyRegistrar storageProperties(@Qualifier("seaweedfs") GenericContainer<?> seaweedfs) {
        return registry -> {
            registry.add("galetube.storage.endpoint",
                () -> "http://" + seaweedfs.getHost() + ":" + seaweedfs.getMappedPort(S3_PORT));
            registry.add("galetube.storage.access-key", () -> ACCESS_KEY);
            registry.add("galetube.storage.secret-key", () -> SECRET_KEY);
            registry.add("galetube.storage.bucket", () -> BUCKET);
    };
    }

    @Bean 
    @ServiceConnection 
    RabbitMQContainer rabbitMQContainer() {
        return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.3-management"));
    }
}

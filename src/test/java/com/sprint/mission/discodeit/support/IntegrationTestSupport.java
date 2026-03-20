package com.sprint.mission.discodeit.support;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    private static final String TEST_BUCKET = "test-bucket";

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @ServiceConnection(name = "redis")
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

    @ServiceConnection
    @SuppressWarnings("deprecation")
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    static final LocalStackContainer LOCALSTACK = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3"))
        .withServices(S3);

    static {
        POSTGRES.start();
        REDIS.start();
        KAFKA.start();
        LOCALSTACK.start();
    }

    @BeforeAll
    static void createS3Bucket() {
        try (S3Client s3Client = S3Client.builder()
            .endpointOverride(LOCALSTACK.getEndpointOverride(S3))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
            .region(Region.of(LOCALSTACK.getRegion()))
            .forcePathStyle(true)
            .build()) {

            boolean bucketExists = s3Client.listBuckets().buckets().stream()
                .anyMatch(b -> b.name().equals(TEST_BUCKET));

            if (!bucketExists) {
                s3Client.createBucket(CreateBucketRequest.builder()
                    .bucket(TEST_BUCKET)
                    .build());
            }
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Kafka 설정 - Testcontainers bootstrap servers로 오버라이드
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);

        // LocalStack S3 설정
        registry.add("discodeit.storage.type", () -> "s3");
        registry.add("discodeit.s3.access-key", LOCALSTACK::getAccessKey);
        registry.add("discodeit.s3.secret-key", LOCALSTACK::getSecretKey);
        registry.add("discodeit.s3.region", LOCALSTACK::getRegion);
        registry.add("discodeit.s3.bucket", () -> TEST_BUCKET);
        registry.add("discodeit.s3.endpoint", () -> LOCALSTACK.getEndpointOverride(S3).toString());
    }
}

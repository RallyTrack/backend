package com.rallytrack.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class S3Config {

    // @Value로 application.yml에서 값을 읽어옴
    @Value("${cloud.aws.credentials.access-key}")
    private String accessKey;

    @Value("${cloud.aws.credentials.secret-key}")
    private String secretKey;

    @Value("${cloud.aws.region}")
    private String region;

    // 비어 있으면 실제 AWS S3, 값이 있으면 MinIO 등 S3 호환 스토리지로 접속
    @Value("${cloud.aws.s3.endpoint:}")
    private String endpoint;

    // presigned URL에 서명될 외부 공개 주소 (브라우저·AI 서버가 여는 주소)
    // 비어 있으면 endpoint를 그대로 사용
    @Value("${cloud.aws.s3.public-endpoint:}")
    private String publicEndpoint;

    // AI 서버(같은 LAN)가 여는 주소. 브라우저용 public-endpoint(공개 도메인)와 분리해
    // AI 서버는 Cloudflare를 거치지 않고 LAN으로 직접 스토리지에 접근한다.
    // 비어 있으면 public-endpoint를 그대로 사용
    @Value("${cloud.aws.s3.ai-endpoint:}")
    private String aiEndpoint;

    // MinIO는 path-style(host/bucket/key) 필수, AWS는 false
    @Value("${cloud.aws.s3.path-style:false}")
    private boolean pathStyle;

    // AWS에서 만들어 놓은 클래스. S3 버킷에 파일 업로드, 다운로드, 삭제 등의 기능이 들어있음
    @Bean
    public S3Client s3Client() {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyle)
                        .build());
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        String presignEndpoint = !publicEndpoint.isBlank() ? publicEndpoint : endpoint;
        return buildPresigner(presignEndpoint);
    }

    // AI 서버에 전달할 URL 전용 presigner (LAN 주소로 서명)
    @Bean
    public S3Presigner aiS3Presigner() {
        String presignEndpoint = !aiEndpoint.isBlank() ? aiEndpoint
                : (!publicEndpoint.isBlank() ? publicEndpoint : endpoint);
        return buildPresigner(presignEndpoint);
    }

    private S3Presigner buildPresigner(String presignEndpoint) {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyle)
                        .build());
        if (!presignEndpoint.isBlank()) {
            builder.endpointOverride(URI.create(presignEndpoint));
        }
        return builder.build();
    }
}

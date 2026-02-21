package com.rallytrack.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;

    // application.yml에서 설정값을 읽어와서 필드에 주입.
    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.region}")
    private String region;

    private final S3Presigner s3Presigner;

    public String upLoadFile(MultipartFile file) throws IOException {
        String fileName = "videos/" + UUID.randomUUID() + "_" + file.getOriginalFilename();

        // 파일을 버킷에 넣는 요청
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(fileName)
                .contentType(file.getContentType())
                .build();

        // 실제로 S3에 업로드
        s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));

        // 업로드된 파일의 S3 url. 이 url이 DB의 videos.s3_url에 저장됨
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucket, region, fileName);
    }

    // 임시 url발급 메서드
    public String generatePresignedUrl(String s3Url) {
        // s3Url에서 key 추출
        String key = s3Url.substring(s3Url.indexOf("videos/"));

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .getObjectRequest(getObjectRequest)
                .signatureDuration(Duration.ofHours(1))
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }
}

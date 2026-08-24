package com.rallytrack.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    // 필드명이 S3Config의 빈 이름과 일치해야 올바른 presigner가 주입된다
    private final S3Presigner s3Presigner;    // 브라우저용 (public-endpoint 서명)
    private final S3Presigner aiS3Presigner;  // AI 서버용 (LAN ai-endpoint 서명)

    // DB에는 전체 URL이 아닌 object key(예: videos/uuid_name.mp4)만 저장한다.
    // 스토리지 endpoint(MinIO ↔ AWS)가 바뀌어도 DB 데이터가 유효하도록 하기 위함.
    public String upLoadFile(MultipartFile file) throws IOException {
        String key = "videos/" + UUID.randomUUID() + "_" + file.getOriginalFilename();

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));

        return key;
    }

    public String generatePresignedUrl(String key) {
        return presignGet(s3Presigner, key);
    }

    public String generatePresignedUploadUrl(String key) {
        return presignPut(s3Presigner, key);
    }

    // AI 서버에 전달하는 URL은 LAN 주소로 서명 (Cloudflare 미경유, DNS 불필요)
    public String generateAiPresignedUrl(String key) {
        return presignGet(aiS3Presigner, key);
    }

    public String generateAiPresignedUploadUrl(String key) {
        return presignPut(aiS3Presigner, key);
    }

    private String presignGet(S3Presigner presigner, String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .getObjectRequest(getObjectRequest)
                .signatureDuration(Duration.ofHours(1))
                .build();

        return presigner.presignGetObject(presignRequest).url().toString();
    }

    private String presignPut(S3Presigner presigner, String key) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("video/mp4")
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .putObjectRequest(putObjectRequest)
                .signatureDuration(Duration.ofHours(1))
                .build();

        return presigner.presignPutObject(presignRequest).url().toString();
    }

    public void deleteFile(String key) {
        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        s3Client.deleteObject(deleteRequest);
    }
}

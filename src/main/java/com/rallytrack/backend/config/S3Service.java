package com.rallytrack.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;

    private String bucket;

    private String region;

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
        return String.format("https://%s.s3.%s.amazonaws.com/%s\", bucket, region, fileName");
    }
}

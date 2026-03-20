package com.sprint.mission.discodeit.infrastructure.storage;

import com.sprint.mission.discodeit.binarycontent.domain.BinaryContentStorage;
import com.sprint.mission.discodeit.binarycontent.domain.exception.BinaryContentStorageException;
import com.sprint.mission.discodeit.binarycontent.presentation.dto.BinaryContentDto;
import com.sprint.mission.discodeit.global.config.properties.S3Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@Slf4j
public class S3BinaryContentStorage implements BinaryContentStorage {

    private final String bucket;
    private final S3Client s3Client;

    public S3BinaryContentStorage(
        S3Client s3Client,
        S3Properties s3Properties
    ) {
        this.s3Client = s3Client;
        this.bucket = s3Properties.bucket();
    }

    @Override
    public UUID put(UUID binaryContentId, byte[] bytes) {
        String key = binaryContentId.toString();

        log.debug("S3 스토리지 파일 저장 시도: key={}, size={}", key, bytes.length);

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

            s3Client.putObject(request, RequestBody.fromBytes(bytes));

            log.info("S3 스토리지 파일 저장 완료: key={}", key);

            return binaryContentId;
        } catch (S3Exception e) {
            log.error("S3 스토리지 파일 저장 실패: key={}", key, e);

            throw new BinaryContentStorageException(e);
        }
    }

    @Override
    public ResponseEntity<byte[]> download(BinaryContentDto metaData) {
        String key = metaData.id().toString();

        log.debug("S3 파일 다운로드 시도: key={}", key);

        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

            ResponseInputStream<GetObjectResponse> s3Object =
                s3Client.getObject(getObjectRequest);
            byte[] content = s3Object.readAllBytes();

            ContentDisposition contentDisposition = ContentDisposition.inline()
                .filename(metaData.fileName(), StandardCharsets.UTF_8)
                .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(contentDisposition);
            headers.setContentType(MediaType.parseMediaType(metaData.contentType()));
            headers.setContentLength(content.length);

            log.info("S3 파일 다운로드 완료: key={}, size={}", key, content.length);

            return ResponseEntity.ok().headers(headers).body(content);
        } catch (S3Exception e) {
            log.error("S3 파일 다운로드 실패: key={}", key, e);

            throw new BinaryContentStorageException(e);
        } catch (IOException e) {
            log.error("S3 파일 읽기 실패: key={}", key, e);

            throw new BinaryContentStorageException(e);
        }
    }
}

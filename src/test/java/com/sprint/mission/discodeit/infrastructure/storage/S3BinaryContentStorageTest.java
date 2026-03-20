package com.sprint.mission.discodeit.infrastructure.storage;

import com.sprint.mission.discodeit.binarycontent.domain.BinaryContentStatus;
import com.sprint.mission.discodeit.binarycontent.domain.exception.BinaryContentStorageException;
import com.sprint.mission.discodeit.binarycontent.presentation.dto.BinaryContentDto;
import com.sprint.mission.discodeit.global.config.properties.S3Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("S3BinaryContentStorage 단위 테스트")
class S3BinaryContentStorageTest {

    @Mock
    private S3Client s3Client;

    private S3BinaryContentStorage storage;

    private static final String TEST_BUCKET = "test-bucket";

    @BeforeEach
    void setUp() {
        S3Properties s3Properties = new S3Properties(
            "accessKey", "secretKey", "us-east-1", TEST_BUCKET, null, Duration.ofMinutes(10)
        );
        storage = new S3BinaryContentStorage(s3Client, s3Properties);
    }

    @Nested
    @DisplayName("put")
    class Put {

        @Test
        @DisplayName("파일 업로드 성공 시 binaryContentId 반환")
        void put_success_returnsBinaryContentId() {
            // given
            UUID binaryContentId = UUID.randomUUID();
            byte[] bytes = "test file content".getBytes();

            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());

            // when
            UUID result = storage.put(binaryContentId, bytes);

            // then
            assertThat(result).isEqualTo(binaryContentId);
        }

        @Test
        @DisplayName("올바른 bucket과 key로 S3에 업로드")
        void put_usesCorrectBucketAndKey() {
            // given
            UUID binaryContentId = UUID.randomUUID();
            byte[] bytes = "test content".getBytes();

            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());

            ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);

            // when
            storage.put(binaryContentId, bytes);

            // then
            then(s3Client).should().putObject(requestCaptor.capture(), any(RequestBody.class));

            PutObjectRequest request = requestCaptor.getValue();
            assertThat(request.bucket()).isEqualTo(TEST_BUCKET);
            assertThat(request.key()).isEqualTo(binaryContentId.toString());
        }

        @Test
        @DisplayName("S3 업로드 실패 시 BinaryContentStorageException 발생")
        void put_s3Error_throwsBinaryContentStorageException() {
            // given
            UUID binaryContentId = UUID.randomUUID();
            byte[] bytes = "test content".getBytes();

            AwsErrorDetails errorDetails = AwsErrorDetails.builder()
                .errorCode("AccessDenied")
                .errorMessage("Access Denied")
                .build();

            S3Exception s3Exception = (S3Exception) S3Exception.builder()
                .awsErrorDetails(errorDetails)
                .message("Access Denied")
                .build();

            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willThrow(s3Exception);

            // when & then
            assertThatThrownBy(() -> storage.put(binaryContentId, bytes))
                .isInstanceOf(BinaryContentStorageException.class);
        }

        @Test
        @DisplayName("빈 바이트 배열도 업로드 가능")
        void put_emptyBytes_uploadsSuccessfully() {
            // given
            UUID binaryContentId = UUID.randomUUID();
            byte[] bytes = new byte[0];

            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());

            // when
            UUID result = storage.put(binaryContentId, bytes);

            // then
            assertThat(result).isEqualTo(binaryContentId);
        }

        @Test
        @DisplayName("대용량 파일 업로드")
        void put_largeFile_uploadsSuccessfully() {
            // given
            UUID binaryContentId = UUID.randomUUID();
            byte[] bytes = new byte[10 * 1024 * 1024]; // 10MB

            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());

            // when
            UUID result = storage.put(binaryContentId, bytes);

            // then
            assertThat(result).isEqualTo(binaryContentId);
        }
    }

    @Nested
    @DisplayName("download")
    class Download {

        @Test
        @DisplayName("다운로드 요청 시 파일 바이트와 올바른 헤더 반환")
        void download_returnsFileContent() {
            // given
            UUID contentId = UUID.randomUUID();
            BinaryContentDto metaData = new BinaryContentDto(
                contentId, "test.png", 1024L, "image/png", BinaryContentStatus.SUCCESS
            );

            byte[] fileBytes = "fake image content".getBytes();
            GetObjectResponse getObjectResponse = GetObjectResponse.builder().build();
            ResponseInputStream<GetObjectResponse> responseStream =
                new ResponseInputStream<>(getObjectResponse, new ByteArrayInputStream(fileBytes));

            given(s3Client.getObject(any(GetObjectRequest.class))).willReturn(responseStream);

            // when
            ResponseEntity<byte[]> response = storage.download(metaData);

            // then
            assertThat(response.getStatusCodeValue()).isEqualTo(200);
            assertThat(response.getBody()).isEqualTo(fileBytes);
            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
            assertThat(response.getHeaders().getContentDisposition().isInline()).isTrue();
            assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("test.png");
        }

        @Test
        @DisplayName("파일명에 한글이 포함된 경우 Content-Disposition 헤더에 올바르게 인코딩")
        void download_koreanFileName_encodedCorrectly() {
            // given
            UUID contentId = UUID.randomUUID();
            BinaryContentDto metaData = new BinaryContentDto(
                contentId, "테스트파일.png", 1024L, "image/png", BinaryContentStatus.SUCCESS
            );

            byte[] fileBytes = "fake image content".getBytes();
            GetObjectResponse getObjectResponse = GetObjectResponse.builder().build();
            ResponseInputStream<GetObjectResponse> responseStream =
                new ResponseInputStream<>(getObjectResponse, new ByteArrayInputStream(fileBytes));

            given(s3Client.getObject(any(GetObjectRequest.class))).willReturn(responseStream);

            // when
            ResponseEntity<byte[]> response = storage.download(metaData);

            // then
            assertThat(response.getStatusCodeValue()).isEqualTo(200);
            assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("테스트파일.png");
        }

        @Test
        @DisplayName("S3 다운로드 실패 시 BinaryContentStorageException 발생")
        void download_s3Error_throwsBinaryContentStorageException() {
            // given
            UUID contentId = UUID.randomUUID();
            BinaryContentDto metaData = new BinaryContentDto(
                contentId, "test.png", 1024L, "image/png", BinaryContentStatus.SUCCESS
            );

            AwsErrorDetails errorDetails = AwsErrorDetails.builder()
                .errorCode("NoSuchKey")
                .errorMessage("The specified key does not exist")
                .build();

            S3Exception s3Exception = (S3Exception) S3Exception.builder()
                .awsErrorDetails(errorDetails)
                .message("NoSuchKey")
                .build();

            given(s3Client.getObject(any(GetObjectRequest.class))).willThrow(s3Exception);

            // when & then
            assertThatThrownBy(() -> storage.download(metaData))
                .isInstanceOf(BinaryContentStorageException.class);
        }

        @Test
        @DisplayName("다양한 컨텐츠 타입에 대해 정상 동작")
        void download_variousContentTypes_worksCorrectly() {
            // given
            UUID contentId = UUID.randomUUID();
            BinaryContentDto pdfMetaData = new BinaryContentDto(
                contentId, "document.pdf", 2048L, "application/pdf", BinaryContentStatus.SUCCESS
            );

            byte[] fileBytes = "fake pdf content".getBytes();
            GetObjectResponse getObjectResponse = GetObjectResponse.builder().build();
            ResponseInputStream<GetObjectResponse> responseStream =
                new ResponseInputStream<>(getObjectResponse, new ByteArrayInputStream(fileBytes));

            given(s3Client.getObject(any(GetObjectRequest.class))).willReturn(responseStream);

            // when
            ResponseEntity<byte[]> response = storage.download(pdfMetaData);

            // then
            assertThat(response.getStatusCodeValue()).isEqualTo(200);
            assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PDF);
        }
    }
}

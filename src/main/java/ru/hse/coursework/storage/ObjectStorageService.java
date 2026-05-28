package ru.hse.coursework.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import ru.hse.coursework.config.MinioStorageProperties;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;

@Service
@ConditionalOnBean(MinioClient.class)
public class ObjectStorageService {
    private static final Logger log = LoggerFactory.getLogger(ObjectStorageService.class);

    private final MinioClient minioClient;
    private final MinioStorageProperties properties;

    public ObjectStorageService(MinioClient minioClient, MinioStorageProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    public StoredObject putImage(byte[] bytes, String extension, String contentType) {
        try {
            ensureBucket();
            String normalizedExtension = extension == null || extension.isBlank() ? "jpg" : extension;
            String objectName = "cards/" + UUID.randomUUID() + "." + normalizedExtension;
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectName)
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType(contentType)
                    .build());
            return new StoredObject(objectName, contentType, bytes.length);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to upload image to object storage", exception);
        }
    }

    public InputStream get(String storageKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(storageKey)
                    .build());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read object from storage: " + storageKey, exception);
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(properties.bucket())
                .build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
            log.info("Created MinIO bucket {}", properties.bucket());
        }
    }
}

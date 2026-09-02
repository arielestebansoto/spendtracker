package com.arielsoto.spendtracker.storage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@ConditionalOnBean(S3Client.class)
public class S3StorageService implements FileStorageService {

    private final S3Client s3Client;
    private final String bucketName;

    public S3StorageService(
        S3Client s3Client,
        S3Properties properties
    ) {
        this.s3Client = s3Client;
        this.bucketName = properties.bucketName();
    }

    @Override
    public StoredFile store(
        MultipartFile file,
        String key
    ) {
        try {
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .build(),
                RequestBody.fromInputStream(
                    file.getInputStream(),
                    file.getSize()
                )
            );

            return new StoredFile(
                key,
                file.getContentType(),
                file.getSize()
            );

        } catch (IOException e) {
            throw new RuntimeException(
                "Failed to upload file to S3",
                e
            );
        }
    }

    @Override
    public Resource load(String key) {
        try {
            var response = s3Client.getObject(
                GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build()
            );

            byte[] data = response.readAllBytes();

            return new ByteArrayResource(data);

        } catch (IOException e) {
            throw new RuntimeException(
                "Failed to load file from S3: " + key,
                e
            );
        }
    }

    @Override
    public void deleteFile(String key) {
        s3Client.deleteObject(
            DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()
        );
    }

    @Override
    public void deleteDirectory(String directory) {
        String prefix = directory.endsWith("/")
            ? directory
            : directory + "/";

        List<ObjectIdentifier> objectsToDelete = new ArrayList<>();
        String continuationToken = null;

        do {
            var listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .continuationToken(continuationToken)
                .build();

            var listResponse = s3Client.listObjectsV2(
                listRequest
            );

            listResponse.contents().forEach(
                object -> objectsToDelete.add(
                    ObjectIdentifier.builder()
                        .key(object.key())
                        .build()
                )
            );

            continuationToken = listResponse.isTruncated()
                ? listResponse.nextContinuationToken()
                : null;

        } while (continuationToken != null);

        if (!objectsToDelete.isEmpty()) {
            s3Client.deleteObjects(
                DeleteObjectsRequest.builder()
                    .bucket(bucketName)
                    .delete(
                        Delete.builder()
                            .objects(objectsToDelete)
                            .build()
                    )
                    .build()
            );
        }
    }
}

package com.system_share_documents.AppCommonService.rest.minio;

import com.system_share_documents.AppCommonService.config.properties.MinioProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Repository
public class MinioStorageRestImpl implements MinioStorageRest {

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private MinioProperties minioProperties;

    @Override
    public String generatePreSignedPutUrl(String objectKey, int expirySeconds) throws Exception {
        return minioClient.getPresignedObjectUrl(
                io.minio.GetPresignedObjectUrlArgs.builder()
                        .bucket(minioProperties.getBucket())
                        .object(objectKey)
                        .method(io.minio.http.Method.PUT)
                        .expiry(expirySeconds, TimeUnit.MINUTES)
                        .build()
        );
    }

    @Override
    public String generatePreSignedGetUrl(String objectKey, int expirySeconds) throws Exception {
        return minioClient.getPresignedObjectUrl(
                io.minio.GetPresignedObjectUrlArgs.builder()
                        .bucket(minioProperties.getBucket())
                        .object(objectKey)
                        .method(io.minio.http.Method.GET)
                        .expiry(expirySeconds, TimeUnit.MINUTES)
                        .build()
        );
    }

    @Override
    public byte[] getObjectBytes(String objectKey) throws Exception {
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(minioProperties.getBucket())
                        .object(objectKey)
                        .build()
        )) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int bytesRead;
            while ((bytesRead = stream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, bytesRead);
            }
            return buffer.toByteArray();
        }
    }

    @Override
    public void putObjectBytes(String objectKey, byte[] data, String contentType) throws Exception {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectKey)
                            .contentType(contentType)
                            .stream(inputStream, data.length, -1)
                            .build()
            );
        }
    }

    @Override
    public InputStream getObjectStream(String objectKey) throws Exception {
        return minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(minioProperties.getBucket())
                        .object(objectKey)
                        .build()
        );
    }

    @Override
    public boolean objectExists(String objectKey) throws Exception {
        return objectExists(minioProperties.getBucket(), objectKey);
    }

    @Override
    public boolean objectExists(String bucket, String objectKey) throws Exception {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
            return true;
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                return false;
            }
            throw e;
        }
    }
}


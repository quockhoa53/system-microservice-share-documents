package com.system_share_documents.AppCommonService.rest.minio;

import com.system_share_documents.AppCommonService.config.properties.MinioProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

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
                        .expiry(expirySeconds)
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
                        .expiry(expirySeconds)
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
}


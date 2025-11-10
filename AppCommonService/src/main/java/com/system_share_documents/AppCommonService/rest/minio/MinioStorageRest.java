package com.system_share_documents.AppCommonService.rest.minio;

import java.io.InputStream;

public interface MinioStorageRest {
    String generatePreSignedPutUrl(String objectKey, int expirySeconds) throws Exception;
    String generatePreSignedGetUrl(String objectKey, int expirySeconds) throws Exception;
    byte[] getObjectBytes(String objectKey) throws Exception;
    void putObjectBytes(String objectKey, byte[] data, String contentType) throws Exception;
    InputStream getObjectStream(String objectKey) throws Exception;
}

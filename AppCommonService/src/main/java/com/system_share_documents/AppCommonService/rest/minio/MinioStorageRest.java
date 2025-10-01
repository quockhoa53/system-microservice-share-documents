package com.system_share_documents.AppCommonService.rest.minio;

public interface MinioStorageRest {
    String generatePreSignedPutUrl(String objectKey, int expirySeconds) throws Exception;
    String generatePreSignedGetUrl(String objectKey, int expirySeconds) throws Exception;
}

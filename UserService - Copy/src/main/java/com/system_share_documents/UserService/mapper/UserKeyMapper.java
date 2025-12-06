package com.system_share_documents.UserService.mapper;

import com.system_share_documents.UserService.dto.response.PublicKeyResponse;
import com.system_share_documents.UserService.entity.UserKey;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface UserKeyMapper {

    @Mapping(source = "id",                 target = "id")
    @Mapping(source = "keyType",            target = "keyType")
    @Mapping(source = "publicKey",          target = "publicKeyArmored")
    @Mapping(source = "keyFingerprint",     target = "fingerprint")
    @Mapping(source = "isPrimary",          target = "primary")
    @Mapping(source = "createdAt",          target = "createdAt")
    @Mapping(source = "revokedAt",          target = "revokedAt")
    PublicKeyResponse toResponse(UserKey entity);

    List<PublicKeyResponse> toResponses(List<UserKey> entities);
}
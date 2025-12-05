package com.system_share_documents.UserService.mapper;

import com.system_share_documents.UserService.dto.response.GroupResponse;
import com.system_share_documents.UserService.entity.Group;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface GroupMapper {

    // đơn giản: map 1–1, ownerId sẽ set ở service
    @Mapping(target = "ownerId", expression = "java(group.getOwner() != null ? group.getOwner().getId() : null)")
    @Mapping(target = "memberCount", ignore = true) // sẽ set ở service
    GroupResponse toResponse(Group group);
}
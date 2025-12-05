package com.system_share_documents.UserService.repository;

import com.system_share_documents.UserService.entity.GroupMember;
import com.system_share_documents.UserService.entity.GroupMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMemberId> {
    long countByGroup_Id(UUID groupId);
    List<GroupMember> findByUser_Id(UUID userId);

    List<GroupMember> findByGroup_Id(UUID groupId);

    Optional<GroupMember> findByGroup_IdAndUser_Id(UUID groupId, UUID userId);
}
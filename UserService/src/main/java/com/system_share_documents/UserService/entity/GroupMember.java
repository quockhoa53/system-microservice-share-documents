package com.system_share_documents.UserService.entity;

import jakarta.persistence.*;
import lombok.*;

import java.sql.Timestamp;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "group_members")
@IdClass(GroupMemberId.class)
public class GroupMember {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    Group group; // nhóm mà user tham gia

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    User user; // thành viên thuộc nhóm

    @Column(nullable = false, length = 32)
    String role; // vai trò của user trong nhóm: "owner", "admin", "member"

    @Column(name = "joined_at", nullable = false, updatable = false)
    Timestamp joinedAt; // thời điểm user tham gia nhóm
}

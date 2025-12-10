package com.system_share_documents.UserService.entity;

import jakarta.persistence.*;
import lombok.*;

import java.sql.Timestamp;
import java.util.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "groups")
public class Group {

    @Id
    @GeneratedValue
    UUID id; // định danh duy nhất của nhóm

    @Column(nullable = false, length = 256)
    String name; // tên của nhóm (ví dụ: "Nhóm nghiên cứu AI")

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    User owner; // user tạo ra nhóm (chủ nhóm)

    @Column(columnDefinition = "text")
    String description; // mô tả ngắn gọn về nhóm

    @Column
    Short visibility = 0; // mức độ hiển thị: 0=private (chỉ thành viên), 1=internal, 2=public

    @Column(name = "keycloak_group_id", unique = true)
    String keycloakGroupId; // ID của group trong Keycloak

    @Column(name = "created_at", nullable = false, updatable = false)
    Timestamp createdAt; // thời điểm tạo nhóm
}

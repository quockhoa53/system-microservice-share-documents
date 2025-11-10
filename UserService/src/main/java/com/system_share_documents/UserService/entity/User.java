package com.system_share_documents.UserService.entity;

import jakarta.persistence.*;
import lombok.*;

import java.sql.Timestamp;
import java.util.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "users")
public class User {

    @Id
    UUID id; // định danh duy nhất của user (UUID)

    @Column(nullable = false, unique = true, length = 128)
    String username; // tên đăng nhập duy nhất trong hệ thống

    @Column(nullable = false, unique = true, length = 256)
    String email; // email duy nhất của user

    @Column(name = "full_name", length = 256)
    String fullName; // tên đầy đủ để hiển thị

    @Column(nullable = false)
    Short status = 1; // trạng thái tài khoản: 1=active, 0=disabled

    @Column(name = "created_at", nullable = false, updatable = false)
    Timestamp createdAt; // thời điểm tạo tài khoản

    @Column(name = "updated_at", nullable = false)
    Timestamp updatedAt; // thời điểm cập nhật gần nhất

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> profile;
}

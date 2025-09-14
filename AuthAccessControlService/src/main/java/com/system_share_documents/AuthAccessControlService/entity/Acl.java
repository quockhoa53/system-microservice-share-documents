package com.system_share_documents.AuthAccessControlService.entity;

import jakarta.persistence.*;
import lombok.*;
import java.sql.Timestamp;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "acls")
public class Acl {

    @Id
    @GeneratedValue
    private UUID id;
    // UUID duy nhất cho mỗi ACL entry

    @Column(name = "object_type", nullable = false, length = 32)
    private String objectType;
    // Loại đối tượng được cấp quyền: 'document', 'group', 'folder'...

    @Column(name = "object_id", nullable = false, length = 36)
    private String objectId;
    // ID của đối tượng (document_id, group_id...)

    @Column(name = "subject_type", nullable = false, length = 16)
    private String subjectType;
    // Loại chủ thể được cấp quyền: 'user', 'group', 'role'

    @Column(name = "subject_id", nullable = false, length = 36)
    private String subjectId;
    // ID của chủ thể tương ứng (user_id, group_id, role_id)

    @Column(name = "permission", nullable = false, length = 64)
    private String permission;
    // Quyền hạn chi tiết: ví dụ 'document.read', 'document.write', 'group.manage'

    @Column(name = "granted_by", length = 36)
    private String grantedBy;
    // ID user cấp quyền (tham chiếu users.id)

    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;
    // Thời điểm tạo ACL entry
}

package com.system_share_documents.UserService.service;

import org.springframework.security.core.Authentication;

public interface UserKeyBackupService {
    /**
     * Lấy encrypted backup của user hiện tại
     * Server không thể đọc được nội dung - chỉ trả về encrypted blob
     * @return encrypted backup hoặc null nếu không có
     */
    String getMyBackup(Authentication auth);

    /**
     * Lưu hoặc cập nhật encrypted backup
     * Server chỉ lưu encrypted blob, không thể đọc được
     */
    void saveBackup(String encryptedBackup, Authentication auth);
}

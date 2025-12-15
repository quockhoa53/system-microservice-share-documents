package com.system_share_documents.UserService.service.impl;

import com.system_share_documents.UserService.entity.User;
import com.system_share_documents.UserService.entity.UserKeyBackup;
import com.system_share_documents.UserService.exception.AppException;
import com.system_share_documents.UserService.exception.errorcode.KeyErrorCode;
import com.system_share_documents.UserService.repository.UserKeyBackupRepository;
import com.system_share_documents.UserService.repository.UserRepository;
import com.system_share_documents.UserService.service.UserKeyBackupService;
import com.system_share_documents.UserService.utils.SecurityUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserKeyBackupServiceImpl implements UserKeyBackupService {

    private final UserRepository userRepository;
    private final UserKeyBackupRepository backupRepository;

    @Override
    @Transactional
    public String getMyBackup(Authentication auth) {
        UUID currentUserId = SecurityUtils.requireCurrentUserId(auth, userRepository);
        return backupRepository.findByUser_Id(currentUserId)
                .map(UserKeyBackup::getEncryptedBackup)
                .orElse(null); // Trả về null nếu không có backup
    }

    @Override
    @Transactional
    public void saveBackup(String encryptedBackup, Authentication auth) {
        UUID currentUserId = SecurityUtils.requireCurrentUserId(auth, userRepository);
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(KeyErrorCode.USER_NOT_FOUND));

        UserKeyBackup backup = backupRepository.findByUser_Id(currentUserId)
                .orElse(null);

        Timestamp now = new Timestamp(System.currentTimeMillis());

        if (backup == null) {
            // Tạo mới
            backup = UserKeyBackup.builder()
                    .user(user)
                    .encryptedBackup(encryptedBackup)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
        } else {
            // Cập nhật
            backup.setEncryptedBackup(encryptedBackup);
            backup.setUpdatedAt(now);
        }

        backupRepository.save(backup);
    }
}

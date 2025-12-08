package com.system_share_documents.AppCommonService.enums;

public enum ActionLog {
    // Authentication & User Management
    LOGIN,
    LOGOUT,
    REGISTER,
    UPDATE_PROFILE,
    
    // Document Operations
    INIT_UPLOAD,
    COMPLETE_UPLOAD,
    WATERMARK,
    DOWNLOAD,
    
    // Access Control
    GRANT_ACCESS,
    REVOKE_GRANT_ACCESS,
    VIEW,
    CHECK
}

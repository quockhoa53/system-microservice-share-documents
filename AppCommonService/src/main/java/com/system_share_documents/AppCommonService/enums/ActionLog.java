package com.system_share_documents.AppCommonService.enums;

public enum ActionLog {
    // Authentication & User Management
    LOGIN,
    LOGOUT,
    REGISTER,
    UPDATE_PROFILE,
    CHANGE_PASSWORD,
    
    // Document Operations
    INIT_UPLOAD,
    COMPLETE_UPLOAD,
    WATERMARK,
    DOWNLOAD,
    PREVIEW,
    DELETE_DOCUMENT,

    // Document Version
    DELETE_VERSIONS,

    // Group
    DISSOLVE_GROUP,
    
    // Access Control
    GRANT_ACCESS,
    REVOKE_GRANT_ACCESS,
    REQUEST_ACCESS,
    VIEW,
    CHECK
}

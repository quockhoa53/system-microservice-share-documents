package com.system_share_documents.AppCommonService.cache.user;

import com.system_share_documents.AppCommonService.dto.response.UserCacheResponse;

import java.util.List;

/**
 * Service để đọc user cache từ Redis (shared cache)
 * Tất cả services có thể dùng service này để lấy user info mà không cần gọi UserService API
 */
public interface UserCacheService {

    /**
     * Lấy user info từ cache theo userId
     * @param userId User ID
     * @return UserCacheResponse hoặc null nếu không tìm thấy
     */
    UserCacheResponse getUserFromCache(String userId);

    /**
     * Lấy user info từ cache cho nhiều userIds (batch)
     * @param userIds List user IDs
     * @return List UserCacheResponse (chỉ những user tìm thấy trong cache)
     */
    List<UserCacheResponse> getUsersFromCache(List<String> userIds);
}
















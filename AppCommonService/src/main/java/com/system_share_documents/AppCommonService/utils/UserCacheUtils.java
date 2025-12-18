package com.system_share_documents.AppCommonService.utils;

import com.system_share_documents.AppCommonService.cache.user.UserCacheService;
import com.system_share_documents.AppCommonService.dto.response.UserCacheResponse;
import org.springframework.stereotype.Component;

/**
 * Utility class để các service dễ dàng lấy user info từ cache
 * Ví dụ: DocumentService có thể dùng để lấy fullname/email của user
 */
@Component
public class UserCacheUtils {

    private static UserCacheService userCacheService;

    public UserCacheUtils(UserCacheService userCacheService) {
        UserCacheUtils.userCacheService = userCacheService;
    }

    /**
     * Lấy fullname của user từ cache
     * @param userId User ID
     * @return Fullname hoặc null nếu không tìm thấy
     */
    public static String getUserFullName(String userId) {
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        UserCacheResponse user = userCacheService.getUserFromCache(userId);
        return user != null ? user.getFullName() : null;
    }

    /**
     * Lấy email của user từ cache
     * @param userId User ID
     * @return Email hoặc null nếu không tìm thấy
     */
    public static String getUserEmail(String userId) {
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        UserCacheResponse user = userCacheService.getUserFromCache(userId);
        return user != null ? user.getEmail() : null;
    }

    /**
     * Lấy username của user từ cache
     * @param userId User ID
     * @return Username hoặc null nếu không tìm thấy
     */
    public static String getUserName(String userId) {
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        UserCacheResponse user = userCacheService.getUserFromCache(userId);
        return user != null ? user.getUsername() : null;
    }

    /**
     * Lấy toàn bộ user info từ cache
     * @param userId User ID
     * @return UserCacheResponse hoặc null nếu không tìm thấy
     */
    public static UserCacheResponse getUserInfo(String userId) {
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        return userCacheService.getUserFromCache(userId);
    }

    /**
     * Check user có tồn tại trong cache không
     * @param userId User ID
     * @return true nếu user tồn tại trong cache
     */
    public static Boolean checkUserExists(String userId) {
        UserCacheResponse user = getUserInfo(userId);
        return user != null;
    }
}


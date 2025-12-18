package com.system_share_documents.UserService.service.cache;

import java.util.UUID;

/**
 * Interface để xử lý cache cho các loại entity khác nhau
 * Cho phép dễ dàng mở rộng để thêm loại cache mới
 * 
 * @param <T> Loại entity cần cache
 */
public interface CacheHandler<T> {
    
    /**
     * Tên của loại cache (vd: "user", "group", "document")
     */
    String getCacheType();
    
    /**
     * Cache một entity cụ thể theo ID
     * @param id ID của entity
     * @return số lượng record đã cache
     */
    int cacheById(UUID id);
    
    /**
     * Cache tất cả entities của loại này
     * @return số lượng record đã cache
     */
    int cacheAll();
    
    /**
     * Xóa cache của một entity cụ thể
     * @param id ID của entity
     */
    void evictCache(UUID id);
    
    /**
     * Xóa toàn bộ cache của loại này
     */
    void evictAllCache();
    
    /**
     * Lấy entity từ cache
     * @param id ID của entity
     * @return Entity nếu tồn tại trong cache, null nếu không
     */
    T getFromCache(UUID id);
    
    /**
     * Kiểm tra entity có trong cache hay không
     * @param id ID của entity
     * @return true nếu có trong cache
     */
    boolean existsInCache(UUID id);
}











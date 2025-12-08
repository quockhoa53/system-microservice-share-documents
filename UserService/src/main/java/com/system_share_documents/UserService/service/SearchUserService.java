package com.system_share_documents.UserService.service;

import com.system_share_documents.UserService.dto.response.UserResponse;

import java.util.List;

public interface SearchUserService {
    /**
     * Tìm kiếm user theo username hoặc email từ Elasticsearch
     * Hỗ trợ real-time search với prefix matching
     *
     * @param query từ khóa tìm kiếm (username hoặc email)
     * @param limit số lượng kết quả tối đa (mặc định 20)
     * @return danh sách user khớp với query
     */
    List<UserResponse> searchUsers(String query, Integer limit);
}

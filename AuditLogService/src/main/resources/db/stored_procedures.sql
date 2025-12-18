-- =====================================================
-- Stored Procedures cho AuditLog Repository
-- =====================================================

-- 1. Tìm audit logs theo user_id, sắp xếp theo created_at DESC
CREATE OR REPLACE FUNCTION sp_find_by_user_id(
    p_user_id VARCHAR(36)
)
RETURNS TABLE (
    id BIGINT,
    user_id VARCHAR(36),
    affected_users TEXT,
    action VARCHAR(64),
    document_id VARCHAR(36),
    object_type VARCHAR(32),
    status VARCHAR(32),
    error_reason VARCHAR(256),
    ip VARCHAR(64),
    user_agent TEXT,
    request TEXT,
    metadata TEXT,
    type_log VARCHAR(16),
    created_at TIMESTAMP
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        al.id,
        al.user_id,
        al.affected_users,
        al.action,
        al.document_id,
        al.object_type,
        al.status,
        al.error_reason,
        al.ip,
        al.user_agent,
        al.request,
        al.metadata,
        al.type_log,
        al.created_at
    FROM audit_logs al
    WHERE al.user_id = p_user_id
    ORDER BY al.created_at DESC;
END;
$$ LANGUAGE plpgsql;

-- 2. Tìm audit logs theo user_id và khoảng thời gian
CREATE OR REPLACE FUNCTION sp_find_by_user_id_and_date_range(
    p_user_id VARCHAR(36),
    p_from TIMESTAMP,
    p_to TIMESTAMP
)
RETURNS TABLE (
    id BIGINT,
    user_id VARCHAR(36),
    affected_users TEXT,
    action VARCHAR(64),
    document_id VARCHAR(36),
    object_type VARCHAR(32),
    status VARCHAR(32),
    error_reason VARCHAR(256),
    ip VARCHAR(64),
    user_agent TEXT,
    request TEXT,
    metadata TEXT,
    type_log VARCHAR(16),
    created_at TIMESTAMP
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        al.id,
        al.user_id,
        al.affected_users,
        al.action,
        al.document_id,
        al.object_type,
        al.status,
        al.error_reason,
        al.ip,
        al.user_agent,
        al.request,
        al.metadata,
        al.type_log,
        al.created_at
    FROM audit_logs al
    WHERE al.user_id = p_user_id
      AND al.created_at >= p_from
      AND al.created_at <= p_to
    ORDER BY al.created_at DESC;
END;
$$ LANGUAGE plpgsql;

-- 3. Tìm audit logs theo document_id
CREATE OR REPLACE FUNCTION sp_find_by_document_id(
    p_document_id VARCHAR(36)
)
RETURNS TABLE (
    id BIGINT,
    user_id VARCHAR(36),
    affected_users TEXT,
    action VARCHAR(64),
    document_id VARCHAR(36),
    object_type VARCHAR(32),
    status VARCHAR(32),
    error_reason VARCHAR(256),
    ip VARCHAR(64),
    user_agent TEXT,
    request TEXT,
    metadata TEXT,
    type_log VARCHAR(16),
    created_at TIMESTAMP
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        al.id,
        al.user_id,
        al.affected_users,
        al.action,
        al.document_id,
        al.object_type,
        al.status,
        al.error_reason,
        al.ip,
        al.user_agent,
        al.request,
        al.metadata,
        al.type_log,
        al.created_at
    FROM audit_logs al
    WHERE al.document_id = p_document_id
    ORDER BY al.created_at DESC;
END;
$$ LANGUAGE plpgsql;

-- 4. Tìm tất cả audit logs với filters và pagination
-- Cải thiện: Hỗ trợ partition pruning tốt hơn bằng cách chỉ thêm điều kiện created_at khi có giá trị
CREATE OR REPLACE FUNCTION sp_find_all_with_filters(
    p_user_id VARCHAR(36) DEFAULT NULL,
    p_action VARCHAR(64) DEFAULT NULL,
    p_status VARCHAR(32) DEFAULT NULL,
    p_object_type VARCHAR(32) DEFAULT NULL,
    p_document_id VARCHAR(36) DEFAULT NULL,
    p_from TIMESTAMP DEFAULT NULL,
    p_to TIMESTAMP DEFAULT NULL,
    p_limit INTEGER DEFAULT 100,
    p_offset INTEGER DEFAULT 0
)
RETURNS TABLE (
    id BIGINT,
    user_id VARCHAR(36),
    affected_users TEXT,
    action VARCHAR(64),
    document_id VARCHAR(36),
    object_type VARCHAR(32),
    status VARCHAR(32),
    error_reason VARCHAR(256),
    ip VARCHAR(64),
    user_agent TEXT,
    request TEXT,
    metadata TEXT,
    type_log VARCHAR(16),
    created_at TIMESTAMP
) AS $$
BEGIN
    -- Nếu có date range, sử dụng điều kiện created_at rõ ràng để hỗ trợ partition pruning
    IF p_from IS NOT NULL AND p_to IS NOT NULL THEN
        RETURN QUERY
        SELECT 
            al.id,
            al.user_id,
            al.affected_users,
            al.action,
            al.document_id,
            al.object_type,
            al.status,
            al.error_reason,
            al.ip,
            al.user_agent,
            al.request,
            al.metadata,
            al.type_log,
            al.created_at
        FROM audit_logs al
        WHERE (COALESCE(p_user_id, '') = '' OR al.user_id = p_user_id)
          AND (COALESCE(p_action, '') = '' OR al.action = p_action)
          AND (COALESCE(p_status, '') = '' OR al.status = p_status)
          AND (COALESCE(p_object_type, '') = '' OR al.object_type = p_object_type)
          AND (COALESCE(p_document_id, '') = '' OR al.document_id = p_document_id)
          AND al.created_at >= p_from
          AND al.created_at <= p_to
        ORDER BY al.created_at DESC
        LIMIT p_limit
        OFFSET p_offset;
    ELSIF p_from IS NOT NULL THEN
        RETURN QUERY
        SELECT 
            al.id,
            al.user_id,
            al.affected_users,
            al.action,
            al.document_id,
            al.object_type,
            al.status,
            al.error_reason,
            al.ip,
            al.user_agent,
            al.request,
            al.metadata,
            al.type_log,
            al.created_at
        FROM audit_logs al
        WHERE (COALESCE(p_user_id, '') = '' OR al.user_id = p_user_id)
          AND (COALESCE(p_action, '') = '' OR al.action = p_action)
          AND (COALESCE(p_status, '') = '' OR al.status = p_status)
          AND (COALESCE(p_object_type, '') = '' OR al.object_type = p_object_type)
          AND (COALESCE(p_document_id, '') = '' OR al.document_id = p_document_id)
          AND al.created_at >= p_from
        ORDER BY al.created_at DESC
        LIMIT p_limit
        OFFSET p_offset;
    ELSIF p_to IS NOT NULL THEN
        RETURN QUERY
        SELECT 
            al.id,
            al.user_id,
            al.affected_users,
            al.action,
            al.document_id,
            al.object_type,
            al.status,
            al.error_reason,
            al.ip,
            al.user_agent,
            al.request,
            al.metadata,
            al.type_log,
            al.created_at
        FROM audit_logs al
        WHERE (COALESCE(p_user_id, '') = '' OR al.user_id = p_user_id)
          AND (COALESCE(p_action, '') = '' OR al.action = p_action)
          AND (COALESCE(p_status, '') = '' OR al.status = p_status)
          AND (COALESCE(p_object_type, '') = '' OR al.object_type = p_object_type)
          AND (COALESCE(p_document_id, '') = '' OR al.document_id = p_document_id)
          AND al.created_at <= p_to
        ORDER BY al.created_at DESC
        LIMIT p_limit
        OFFSET p_offset;
    ELSE
        -- Không có date range, không thể sử dụng partition pruning
        RETURN QUERY
        SELECT 
            al.id,
            al.user_id,
            al.affected_users,
            al.action,
            al.document_id,
            al.object_type,
            al.status,
            al.error_reason,
            al.ip,
            al.user_agent,
            al.request,
            al.metadata,
            al.type_log,
            al.created_at
        FROM audit_logs al
        WHERE (COALESCE(p_user_id, '') = '' OR al.user_id = p_user_id)
          AND (COALESCE(p_action, '') = '' OR al.action = p_action)
          AND (COALESCE(p_status, '') = '' OR al.status = p_status)
          AND (COALESCE(p_object_type, '') = '' OR al.object_type = p_object_type)
          AND (COALESCE(p_document_id, '') = '' OR al.document_id = p_document_id)
        ORDER BY al.created_at DESC
        LIMIT p_limit
        OFFSET p_offset;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- 5. Đếm số lượng audit logs với filters
-- Cải thiện: Hỗ trợ partition pruning tốt hơn
CREATE OR REPLACE FUNCTION sp_count_all_with_filters(
    p_user_id VARCHAR(36) DEFAULT NULL,
    p_action VARCHAR(64) DEFAULT NULL,
    p_status VARCHAR(32) DEFAULT NULL,
    p_object_type VARCHAR(32) DEFAULT NULL,
    p_document_id VARCHAR(36) DEFAULT NULL,
    p_from TIMESTAMP DEFAULT NULL,
    p_to TIMESTAMP DEFAULT NULL
)
RETURNS BIGINT AS $$
DECLARE
    v_count BIGINT;
BEGIN
    -- Nếu có date range, sử dụng điều kiện created_at rõ ràng để hỗ trợ partition pruning
    IF p_from IS NOT NULL AND p_to IS NOT NULL THEN
        SELECT COUNT(*) INTO v_count
        FROM audit_logs al
        WHERE (COALESCE(p_user_id, '') = '' OR al.user_id = p_user_id)
          AND (COALESCE(p_action, '') = '' OR al.action = p_action)
          AND (COALESCE(p_status, '') = '' OR al.status = p_status)
          AND (COALESCE(p_object_type, '') = '' OR al.object_type = p_object_type)
          AND (COALESCE(p_document_id, '') = '' OR al.document_id = p_document_id)
          AND al.created_at >= p_from
          AND al.created_at <= p_to;
    ELSIF p_from IS NOT NULL THEN
        SELECT COUNT(*) INTO v_count
        FROM audit_logs al
        WHERE (COALESCE(p_user_id, '') = '' OR al.user_id = p_user_id)
          AND (COALESCE(p_action, '') = '' OR al.action = p_action)
          AND (COALESCE(p_status, '') = '' OR al.status = p_status)
          AND (COALESCE(p_object_type, '') = '' OR al.object_type = p_object_type)
          AND (COALESCE(p_document_id, '') = '' OR al.document_id = p_document_id)
          AND al.created_at >= p_from;
    ELSIF p_to IS NOT NULL THEN
        SELECT COUNT(*) INTO v_count
        FROM audit_logs al
        WHERE (COALESCE(p_user_id, '') = '' OR al.user_id = p_user_id)
          AND (COALESCE(p_action, '') = '' OR al.action = p_action)
          AND (COALESCE(p_status, '') = '' OR al.status = p_status)
          AND (COALESCE(p_object_type, '') = '' OR al.object_type = p_object_type)
          AND (COALESCE(p_document_id, '') = '' OR al.document_id = p_document_id)
          AND al.created_at <= p_to;
    ELSE
        SELECT COUNT(*) INTO v_count
        FROM audit_logs al
        WHERE (COALESCE(p_user_id, '') = '' OR al.user_id = p_user_id)
          AND (COALESCE(p_action, '') = '' OR al.action = p_action)
          AND (COALESCE(p_status, '') = '' OR al.status = p_status)
          AND (COALESCE(p_object_type, '') = '' OR al.object_type = p_object_type)
          AND (COALESCE(p_document_id, '') = '' OR al.document_id = p_document_id);
    END IF;
    
    RETURN v_count;
END;
$$ LANGUAGE plpgsql;

-- 6. Đếm số lượng audit logs với filters (cho statistics)
-- Cải thiện: Hỗ trợ partition pruning tốt hơn
CREATE OR REPLACE FUNCTION sp_count_with_filters(
    p_action VARCHAR(64) DEFAULT NULL,
    p_status VARCHAR(32) DEFAULT NULL,
    p_from TIMESTAMP DEFAULT NULL,
    p_to TIMESTAMP DEFAULT NULL
)
RETURNS BIGINT AS $$
DECLARE
    v_count BIGINT;
BEGIN
    -- Nếu có date range, sử dụng điều kiện created_at rõ ràng để hỗ trợ partition pruning
    IF p_from IS NOT NULL AND p_to IS NOT NULL THEN
        SELECT COUNT(*) INTO v_count
        FROM audit_logs al
        WHERE (COALESCE(p_action, '') = '' OR al.action = p_action)
          AND (COALESCE(p_status, '') = '' OR al.status = p_status)
          AND al.created_at >= p_from
          AND al.created_at <= p_to;
    ELSIF p_from IS NOT NULL THEN
        SELECT COUNT(*) INTO v_count
        FROM audit_logs al
        WHERE (COALESCE(p_action, '') = '' OR al.action = p_action)
          AND (COALESCE(p_status, '') = '' OR al.status = p_status)
          AND al.created_at >= p_from;
    ELSIF p_to IS NOT NULL THEN
        SELECT COUNT(*) INTO v_count
        FROM audit_logs al
        WHERE (COALESCE(p_action, '') = '' OR al.action = p_action)
          AND (COALESCE(p_status, '') = '' OR al.status = p_status)
          AND al.created_at <= p_to;
    ELSE
        SELECT COUNT(*) INTO v_count
        FROM audit_logs al
        WHERE (COALESCE(p_action, '') = '' OR al.action = p_action)
          AND (COALESCE(p_status, '') = '' OR al.status = p_status);
    END IF;
    
    RETURN v_count;
END;
$$ LANGUAGE plpgsql;

-- 7. Đếm số lượng audit logs theo action
-- Cải thiện: Hỗ trợ partition pruning tốt hơn
CREATE OR REPLACE FUNCTION sp_count_by_action(
    p_from TIMESTAMP DEFAULT NULL,
    p_to TIMESTAMP DEFAULT NULL
)
RETURNS TABLE (
    action VARCHAR(64),
    count BIGINT
) AS $$
BEGIN
    -- Nếu có date range, sử dụng điều kiện created_at rõ ràng để hỗ trợ partition pruning
    IF p_from IS NOT NULL AND p_to IS NOT NULL THEN
        RETURN QUERY
        SELECT 
            al.action,
            COUNT(*)::BIGINT as count
        FROM audit_logs al
        WHERE al.created_at >= p_from
          AND al.created_at <= p_to
        GROUP BY al.action;
    ELSIF p_from IS NOT NULL THEN
        RETURN QUERY
        SELECT 
            al.action,
            COUNT(*)::BIGINT as count
        FROM audit_logs al
        WHERE al.created_at >= p_from
        GROUP BY al.action;
    ELSIF p_to IS NOT NULL THEN
        RETURN QUERY
        SELECT 
            al.action,
            COUNT(*)::BIGINT as count
        FROM audit_logs al
        WHERE al.created_at <= p_to
        GROUP BY al.action;
    ELSE
        RETURN QUERY
        SELECT 
            al.action,
            COUNT(*)::BIGINT as count
        FROM audit_logs al
        GROUP BY al.action;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- 8. Đếm số lượng audit logs theo status
-- Cải thiện: Hỗ trợ partition pruning tốt hơn
CREATE OR REPLACE FUNCTION sp_count_by_status(
    p_from TIMESTAMP DEFAULT NULL,
    p_to TIMESTAMP DEFAULT NULL
)
RETURNS TABLE (
    status VARCHAR(32),
    count BIGINT
) AS $$
BEGIN
    -- Nếu có date range, sử dụng điều kiện created_at rõ ràng để hỗ trợ partition pruning
    IF p_from IS NOT NULL AND p_to IS NOT NULL THEN
        RETURN QUERY
        SELECT 
            al.status,
            COUNT(*)::BIGINT as count
        FROM audit_logs al
        WHERE al.created_at >= p_from
          AND al.created_at <= p_to
        GROUP BY al.status;
    ELSIF p_from IS NOT NULL THEN
        RETURN QUERY
        SELECT 
            al.status,
            COUNT(*)::BIGINT as count
        FROM audit_logs al
        WHERE al.created_at >= p_from
        GROUP BY al.status;
    ELSIF p_to IS NOT NULL THEN
        RETURN QUERY
        SELECT 
            al.status,
            COUNT(*)::BIGINT as count
        FROM audit_logs al
        WHERE al.created_at <= p_to
        GROUP BY al.status;
    ELSE
        RETURN QUERY
        SELECT 
            al.status,
            COUNT(*)::BIGINT as count
        FROM audit_logs al
        GROUP BY al.status;
    END IF;
END;
$$ LANGUAGE plpgsql;













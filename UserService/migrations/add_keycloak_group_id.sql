-- Migration: Add keycloak_group_id column to groups table
-- Date: 2024
-- Description: Thêm column keycloak_group_id để lưu ID của group trong Keycloak

-- Thêm column keycloak_group_id vào bảng groups
ALTER TABLE groups 
ADD COLUMN IF NOT EXISTS keycloak_group_id VARCHAR(255);

-- Tạo unique constraint để đảm bảo mỗi Keycloak group chỉ map với 1 database group
CREATE UNIQUE INDEX IF NOT EXISTS idx_groups_keycloak_group_id 
ON groups(keycloak_group_id) 
WHERE keycloak_group_id IS NOT NULL;

-- Comment cho column
COMMENT ON COLUMN groups.keycloak_group_id IS 'ID của group trong Keycloak, dùng để đồng bộ 2 chiều';








package com.system_share_documents.UserService.repository;

import com.system_share_documents.UserService.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findById(UUID id);
    
    // Statistics methods
    long count();
    long countByStatus(Short status);
    
    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt >= :from AND u.createdAt <= :to")
    long countByCreatedAtBetween(@Param("from") Timestamp from, @Param("to") Timestamp to);
    
    @Query("SELECT COUNT(u) FROM User u WHERE u.status = :status AND u.createdAt >= :from AND u.createdAt <= :to")
    long countByStatusAndCreatedAtBetween(@Param("status") Short status, @Param("from") Timestamp from, @Param("to") Timestamp to);
    
    // Admin methods - Pagination and search
    Page<User> findAll(Pageable pageable);
    
    @Query("SELECT u FROM User u WHERE " +
           "(:search IS NULL OR :search = '' OR " +
           "LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:status IS NULL OR u.status = :status)")
    Page<User> findAllWithFilters(
            @Param("search") String search,
            @Param("status") Short status,
            Pageable pageable
    );
    
    @Query("SELECT u FROM User u WHERE u.status = :status")
    Page<User> findByStatus(@Param("status") Short status, Pageable pageable);
}

package com.techconnect.repository;

import com.techconnect.entity.User;
import com.techconnect.entity.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByRoleName(RoleName roleName);
    List<User> findByDepartmentId(Long departmentId);
    List<User> findByTeamId(Long teamId);
}

package com.techconnect.repository;

import com.techconnect.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {
    List<Team> findByDepartmentId(Long departmentId);
    Optional<Team> findByNameAndDepartmentId(String name, Long departmentId);
    Optional<Team> findByName(String name);
}

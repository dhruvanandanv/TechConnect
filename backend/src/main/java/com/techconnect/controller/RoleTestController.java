package com.techconnect.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller exposing minimal role-based test endpoints to verify Spring Security
 * authorization matrices without premature ticket business logic.
 */
@RestController
@RequestMapping("/api")
public class RoleTestController {

    @GetMapping("/employee/test")
    public ResponseEntity<Map<String, Object>> employeeTest() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Authorized: Employee endpoint accessed successfully",
                "roleRequirement", "ROLE_EMPLOYEE, ROLE_ENGINEER, ROLE_MANAGER, or ROLE_ADMIN"
        ));
    }

    @GetMapping("/engineer/test")
    public ResponseEntity<Map<String, Object>> engineerTest() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Authorized: Engineer endpoint accessed successfully",
                "roleRequirement", "ROLE_ENGINEER or ROLE_ADMIN"
        ));
    }

    @GetMapping("/manager/test")
    public ResponseEntity<Map<String, Object>> managerTest() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Authorized: Manager endpoint accessed successfully",
                "roleRequirement", "ROLE_MANAGER or ROLE_ADMIN"
        ));
    }

    @GetMapping("/admin/test")
    public ResponseEntity<Map<String, Object>> adminTest() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Authorized: Admin endpoint accessed successfully",
                "roleRequirement", "ROLE_ADMIN"
        ));
    }
}

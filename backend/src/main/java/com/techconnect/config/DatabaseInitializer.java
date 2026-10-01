package com.techconnect.config;

import com.techconnect.entity.*;
import com.techconnect.entity.enums.Priority;
import com.techconnect.entity.enums.RoleName;
import com.techconnect.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final SLARepository slaRepository;
    private final DepartmentRepository departmentRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.dev-users:false}")
    private boolean seedDevUsers;

    @Value("${app.seed.dev-password:}")
    private String devInitialPassword;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Initializing baseline database configurations...");
        initRoles();
        initSlaRules();
        initDepartmentsAndTeams();
        initDefaultUsers();
        log.info("Database baseline initialization completed successfully.");
    }

    private void initRoles() {
        for (RoleName roleName : RoleName.values()) {
            if (!roleRepository.existsByName(roleName)) {
                String description = switch (roleName) {
                    case ROLE_EMPLOYEE -> "Standard corporate employee reporting technical issues";
                    case ROLE_ENGINEER -> "IT support engineer managing and resolving tickets";
                    case ROLE_MANAGER -> "Support manager overseeing team workloads and SLAs";
                    case ROLE_ADMIN -> "System administrator managing users, teams, and configurations";
                };
                roleRepository.save(Role.builder()
                        .name(roleName)
                        .description(description)
                        .build());
                log.info("Seeded role: {}", roleName);
            }
        }
    }

    private void initSlaRules() {
        if (!slaRepository.existsByPriority(Priority.CRITICAL)) {
            slaRepository.save(SLA.builder()
                    .priority(Priority.CRITICAL)
                    .resolutionTimeHours(2)
                    .warningThresholdHours(1)
                    .description("Critical outage affecting business operations (2h SLA)")
                    .build());
        }
        if (!slaRepository.existsByPriority(Priority.HIGH)) {
            slaRepository.save(SLA.builder()
                    .priority(Priority.HIGH)
                    .resolutionTimeHours(4)
                    .warningThresholdHours(2)
                    .description("High impact issue with significant impairment (4h SLA)")
                    .build());
        }
        if (!slaRepository.existsByPriority(Priority.MEDIUM)) {
            slaRepository.save(SLA.builder()
                    .priority(Priority.MEDIUM)
                    .resolutionTimeHours(8)
                    .warningThresholdHours(4)
                    .description("Standard operational inquiry or software glitch (8h SLA)")
                    .build());
        }
        if (!slaRepository.existsByPriority(Priority.LOW)) {
            slaRepository.save(SLA.builder()
                    .priority(Priority.LOW)
                    .resolutionTimeHours(24)
                    .warningThresholdHours(12)
                    .description("Minor issue or non-urgent request (24h SLA)")
                    .build());
        }
        log.info("SLA policy configurations verified.");
    }

    private void initDepartmentsAndTeams() {
        Department itDept = departmentRepository.findByCode("IT-OPS").orElseGet(() ->
                departmentRepository.save(Department.builder()
                        .name("IT Operations & Infrastructure")
                        .code("IT-OPS")
                        .description("Manages internal networking, hardware, and corporate infrastructure")
                        .build())
        );

        Department secDept = departmentRepository.findByCode("INFOSEC").orElseGet(() ->
                departmentRepository.save(Department.builder()
                        .name("Information Security")
                        .code("INFOSEC")
                        .description("Manages access controls, VPN firewalls, and incident response")
                        .build())
        );

        if (teamRepository.findByNameAndDepartmentId("Network Support", itDept.getId()).isEmpty()) {
            teamRepository.save(Team.builder()
                    .name("Network Support")
                    .department(itDept)
                    .description("VPN, Wi-Fi, switches, routers, and connectivity")
                    .build());
        }

        if (teamRepository.findByNameAndDepartmentId("Hardware & Workplace", itDept.getId()).isEmpty()) {
            teamRepository.save(Team.builder()
                    .name("Hardware & Workplace")
                    .department(itDept)
                    .description("Laptops, monitors, peripherals, and workstation setups")
                    .build());
        }

        if (teamRepository.findByNameAndDepartmentId("Identity & Access", secDept.getId()).isEmpty()) {
            teamRepository.save(Team.builder()
                    .name("Identity & Access")
                    .department(secDept)
                    .description("SSO, Active Directory, credentials, and privilege escalation")
                    .build());
        }
        log.info("Departments and Teams baseline verified.");
    }

    private void initDefaultUsers() {
        if (!seedDevUsers) {
            log.info("Development user seeding is disabled (app.seed.dev-users=false). Skipping user seeding.");
            return;
        }

        if (devInitialPassword == null || devInitialPassword.trim().isEmpty()) {
            throw new IllegalStateException(
                "Development user seeding is enabled (app.seed.dev-users=true), but TECHCONNECT_DEV_PASSWORD is not set or is empty. " +
                "Please provide a development password via the TECHCONNECT_DEV_PASSWORD environment variable or disable development seeding."
            );
        }

        log.warn("SECURITY NOTICE: Seeding development user accounts with externalized dev password. " +
                "Ensure app.seed.dev-users=false in production environments.");

        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN).orElse(null);
        Role engineerRole = roleRepository.findByName(RoleName.ROLE_ENGINEER).orElse(null);
        Role managerRole = roleRepository.findByName(RoleName.ROLE_MANAGER).orElse(null);
        Role employeeRole = roleRepository.findByName(RoleName.ROLE_EMPLOYEE).orElse(null);

        Department itDept = departmentRepository.findByCode("IT-OPS").orElse(null);
        Team networkTeam = teamRepository.findByName("Network Support").orElse(null);

        String initialPasswordHash = passwordEncoder.encode(devInitialPassword);

        // Seed Admin user if not exists
        if (!userRepository.existsByEmail("admin@techconnect.com") && adminRole != null) {
            userRepository.save(User.builder()
                    .email("admin@techconnect.com")
                    .password(initialPasswordHash)
                    .firstName("System")
                    .lastName("Administrator")
                    .role(adminRole)
                    .department(itDept)
                    .build());
            log.info("Seeded development admin user: admin@techconnect.com");
        }

        // Seed Engineer user if not exists
        if (!userRepository.existsByEmail("engineer@techconnect.com") && engineerRole != null) {
            userRepository.save(User.builder()
                    .email("engineer@techconnect.com")
                    .password(initialPasswordHash)
                    .firstName("Alex")
                    .lastName("Engineer")
                    .role(engineerRole)
                    .department(itDept)
                    .team(networkTeam)
                    .build());
            log.info("Seeded development engineer user: engineer@techconnect.com");
        }

        // Seed Manager user if not exists
        if (!userRepository.existsByEmail("manager@techconnect.com") && managerRole != null) {
            userRepository.save(User.builder()
                    .email("manager@techconnect.com")
                    .password(initialPasswordHash)
                    .firstName("Sarah")
                    .lastName("Manager")
                    .role(managerRole)
                    .department(itDept)
                    .build());
            log.info("Seeded development manager user: manager@techconnect.com");
        }

        // Seed Employee user if not exists
        if (!userRepository.existsByEmail("employee@techconnect.com") && employeeRole != null) {
            userRepository.save(User.builder()
                    .email("employee@techconnect.com")
                    .password(initialPasswordHash)
                    .firstName("John")
                    .lastName("Doe")
                    .role(employeeRole)
                    .department(itDept)
                    .build());
            log.info("Seeded development employee user: employee@techconnect.com");
        }
    }
}

package com.techconnect.mapper;

import com.techconnect.dto.UserResponse;
import com.techconnect.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toUserResponse(User user) {
        if (user == null) {
            return null;
        }

        String fullName = (user.getFirstName() != null ? user.getFirstName() : "") +
                (user.getLastName() != null && !user.getLastName().isBlank() ? " " + user.getLastName() : "");

        return UserResponse.builder()
                .id(user.getId())
                .name(fullName.trim())
                .email(user.getEmail())
                .role(user.getRole() != null ? user.getRole().getName().name() : null)
                .departmentId(user.getDepartment() != null ? user.getDepartment().getId() : null)
                .departmentName(user.getDepartment() != null ? user.getDepartment().getName() : null)
                .teamId(user.getTeam() != null ? user.getTeam().getId() : null)
                .teamName(user.getTeam() != null ? user.getTeam().getName() : null)
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}

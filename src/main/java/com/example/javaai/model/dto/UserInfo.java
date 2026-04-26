package com.example.javaai.model.dto;

import lombok.Data;

@Data
public class UserInfo {
    private Long id;
    private String username;
    private String email;
    private String systemRole;
}

package com.example.javaai.identity;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GroupContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> GROUP_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> SYSTEM_ROLE = new ThreadLocal<>();

    public static void set(Long userId, Long groupId, String systemRole) {
        USER_ID.set(userId);
        GROUP_ID.set(groupId);
        SYSTEM_ROLE.set(systemRole);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static Long getGroupId() {
        return GROUP_ID.get();
    }

    public static String getSystemRole() {
        return SYSTEM_ROLE.get();
    }

    public static boolean isAdmin() {
        return "ADMIN".equals(SYSTEM_ROLE.get());
    }

    public static void clear() {
        USER_ID.remove();
        GROUP_ID.remove();
        SYSTEM_ROLE.remove();
    }
}

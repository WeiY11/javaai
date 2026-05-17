package com.example.evimind.identity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.evimind.mapper.GroupMapper;
import com.example.evimind.mapper.GroupMemberMapper;
import com.example.evimind.model.entity.Group;
import com.example.evimind.model.entity.GroupMember;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupMapper groupMapper;
    private final GroupMemberMapper groupMemberMapper;

    @Transactional
    public Long getOrCreateDefaultGroupId(Long userId, String username) {
        if (userId == null) {
            return null;
        }

        // 1. 检查用户是否已经属于某个空间/群组
        List<GroupMember> memberships = groupMemberMapper.selectList(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getUserId, userId)
        );

        if (!memberships.isEmpty()) {
            return memberships.get(0).getGroupId();
        }

        // 2. 如果没有任何空间，则自动创建默认空间
        Group group = new Group();
        group.setName((username != null ? username : "User_" + userId) + "的默认空间");
        group.setCreatorId(userId);
        group.setStatus("ACTIVE");
        groupMapper.insert(group);

        // 3. 将当前用户添加为该默认空间的 OWNER
        GroupMember newMember = new GroupMember();
        newMember.setGroupId(group.getId());
        newMember.setUserId(userId);
        newMember.setRole("OWNER");
        groupMemberMapper.insert(newMember);

        log.info("自动创建默认群组空间: {} (groupId={}, userId={})", group.getName(), group.getId(), userId);
        return group.getId();
    }
}

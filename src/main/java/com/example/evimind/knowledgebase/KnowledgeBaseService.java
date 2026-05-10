package com.example.evimind.knowledgebase;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.evimind.identity.GroupContext;
import com.example.evimind.mapper.KbMemberMapper;
import com.example.evimind.mapper.KnowledgeBaseMapper;
import com.example.evimind.model.entity.KbMember;
import com.example.evimind.model.entity.KnowledgeBase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KbMemberMapper kbMemberMapper;

    @Transactional
    public KnowledgeBase create(KnowledgeBase kb) {
        if (kb.getEvidenceThreshold() == null) kb.setEvidenceThreshold(new BigDecimal("0.50"));
        if (kb.getChunkStrategy() == null) kb.setChunkStrategy("PARAGRAPH");
        if (kb.getChunkSize() == null) kb.setChunkSize(500);
        if (kb.getChunkOverlap() == null) kb.setChunkOverlap(100);
        kb.setStatus("ACTIVE");
        kb.setCreatorId(GroupContext.getUserId());
        knowledgeBaseMapper.insert(kb);

        KbMember member = new KbMember();
        member.setKnowledgeBaseId(kb.getId());
        member.setUserId(GroupContext.getUserId());
        member.setRole("OWNER");
        kbMemberMapper.insert(member);

        log.info("Created knowledge base: {} (id={})", kb.getName(), kb.getId());
        return kb;
    }

    public KnowledgeBase update(KnowledgeBase kb) {
        if (!isOwner(kb.getId())) {
            throw new SecurityException("Only OWNER can update knowledge base");
        }
        knowledgeBaseMapper.updateById(kb);
        return kb;
    }

    @Transactional
    public void delete(Long kbId) {
        if (!isOwner(kbId)) {
            throw new SecurityException("Only OWNER can delete knowledge base");
        }
        knowledgeBaseMapper.deleteById(kbId);
        log.info("Deleted knowledge base id={}", kbId);
    }

    public Page<KnowledgeBase> listAccessible(int page, int size) {
        Long userId = GroupContext.getUserId();
        if (userId == null) {
            return new Page<>(page, size);
        }

        List<Long> kbIds = kbMemberMapper.selectList(
                new LambdaQueryWrapper<KbMember>()
                        .eq(KbMember::getUserId, userId)
        ).stream().map(KbMember::getKnowledgeBaseId).collect(Collectors.toList());

        if (kbIds.isEmpty()) {
            return new Page<>(page, size);
        }

        return knowledgeBaseMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<KnowledgeBase>()
                        .in(KnowledgeBase::getId, kbIds)
                        .eq(KnowledgeBase::getStatus, "ACTIVE")
                        .orderByDesc(KnowledgeBase::getCreatedAt)
        );
    }

    public KnowledgeBase getById(Long kbId) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if (kb != null && !isMember(kbId) && !GroupContext.isAdmin()) {
            throw new SecurityException("Access denied: you are not a member of this knowledge base");
        }
        return kb;
    }

    public boolean isOwner(Long kbId) {
        Long userId = GroupContext.getUserId();
        Long count = kbMemberMapper.selectCount(
                new LambdaQueryWrapper<KbMember>()
                        .eq(KbMember::getKnowledgeBaseId, kbId)
                        .eq(KbMember::getUserId, userId)
                        .eq(KbMember::getRole, "OWNER")
        );
        return count > 0;
    }

    public boolean isMember(Long kbId) {
        Long userId = GroupContext.getUserId();
        Long count = kbMemberMapper.selectCount(
                new LambdaQueryWrapper<KbMember>()
                        .eq(KbMember::getKnowledgeBaseId, kbId)
                        .eq(KbMember::getUserId, userId)
        );
        return count > 0;
    }

    @Transactional
    public KbMember addMember(Long kbId, Long userId, String role) {
        if (!isOwner(kbId)) {
            throw new SecurityException("Only OWNER can add members");
        }
        KbMember member = new KbMember();
        member.setKnowledgeBaseId(kbId);
        member.setUserId(userId);
        member.setRole(role != null ? role : "MEMBER");
        kbMemberMapper.insert(member);
        return member;
    }

    @Transactional
    public void removeMember(Long kbId, Long userId) {
        if (!isOwner(kbId)) {
            throw new SecurityException("Only OWNER can remove members");
        }
        kbMemberMapper.delete(
                new LambdaQueryWrapper<KbMember>()
                        .eq(KbMember::getKnowledgeBaseId, kbId)
                        .eq(KbMember::getUserId, userId)
        );
    }

    public List<KbMember> listMembers(Long kbId) {
        if (!isMember(kbId) && !GroupContext.isAdmin()) {
            throw new SecurityException("Access denied: you are not a member of this knowledge base");
        }
        return kbMemberMapper.selectList(
                new LambdaQueryWrapper<KbMember>()
                        .eq(KbMember::getKnowledgeBaseId, kbId)
        );
    }
}

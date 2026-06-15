package com.example.evimind.service;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.evimind.mapper.DocumentChunkEmbeddingMapper;
import com.example.evimind.model.entity.DocumentChunkEmbedding;

@Service
public class DocumentChunkEmbeddingService
    extends ServiceImpl<DocumentChunkEmbeddingMapper, DocumentChunkEmbedding> {}

package com.example.evimind.service;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.evimind.mapper.DocumentChunkMapper;
import com.example.evimind.model.entity.DocumentChunk;

@Service
public class DocumentChunkService extends ServiceImpl<DocumentChunkMapper, DocumentChunk> {}

package com.example.evimind.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.evimind.model.entity.DocumentPermission;

@Mapper
public interface DocumentPermissionMapper extends BaseMapper<DocumentPermission> {

  @Select("SELECT * FROM document_permission WHERE document_id = #{documentId} ORDER BY created_at")
  List<DocumentPermission> findByDocumentId(@Param("documentId") Long documentId);

  @Select(
      "SELECT * FROM document_permission WHERE document_id = #{documentId} AND user_id = #{userId}")
  List<DocumentPermission> findByDocumentAndUser(
      @Param("documentId") Long documentId, @Param("userId") Long userId);

  @Select(
      """
        SELECT DISTINCT dp.document_id FROM document_permission dp
        WHERE dp.user_id = #{userId} AND dp.permission_type = #{permissionType}
        """)
  List<Long> findPermittedDocumentIds(
      @Param("userId") Long userId, @Param("permissionType") String permissionType);
}

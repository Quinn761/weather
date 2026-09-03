package com.weatherhub.ai.store;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiMemoryMapper extends BaseMapper<AiMemory> {

    @Insert("""
            INSERT INTO ai_memory (user_id, mem_key, mem_value, updated_at)
            VALUES (#{userId}, #{memKey}, #{memValue}, NOW())
            ON DUPLICATE KEY UPDATE mem_value = VALUES(mem_value), updated_at = NOW()
            """)
    int upsert(@Param("userId") Long userId, @Param("memKey") String memKey, @Param("memValue") String memValue);
}

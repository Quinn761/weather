package com.weatherhub.ai.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("ai_memory")
public class AiMemory {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String memKey;
    private String memValue;
    private LocalDateTime updatedAt;
}

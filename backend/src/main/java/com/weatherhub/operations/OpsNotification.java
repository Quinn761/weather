package com.weatherhub.operations;
import com.baomidou.mybatisplus.annotation.IdType; import com.baomidou.mybatisplus.annotation.TableId; import com.baomidou.mybatisplus.annotation.TableName; import lombok.Getter; import lombok.Setter; import java.time.LocalDateTime;
@Getter @Setter @TableName("ops_notification") public class OpsNotification { @TableId(type=IdType.AUTO) private Long id; private Long userId; private String type; private String title; private String content; private String targetType; private Long targetId; private LocalDateTime readAt; private LocalDateTime createdAt; }

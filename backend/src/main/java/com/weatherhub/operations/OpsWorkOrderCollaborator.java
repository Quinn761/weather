package com.weatherhub.operations;
import com.baomidou.mybatisplus.annotation.*; import lombok.*; import java.time.LocalDateTime;
@Getter @Setter @TableName("ops_work_order_collaborator") public class OpsWorkOrderCollaborator { @TableId(type=IdType.AUTO) private Long id; private Long workOrderId; private Long userId; private LocalDateTime createdAt; }

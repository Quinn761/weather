package com.weatherhub.operations;
import com.baomidou.mybatisplus.annotation.*; import lombok.*; import java.time.LocalDateTime;
@Getter @Setter @TableName("ops_work_order_progress") public class OpsWorkOrderProgress { @TableId(type=IdType.AUTO) private Long id; private Long workOrderId; private String content; private String imageUrls; private Double longitude; private Double latitude; private String statusAfter; private Long createdBy; private LocalDateTime createdAt; }

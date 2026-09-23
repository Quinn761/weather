package com.weatherhub.operations.dto;
import java.time.LocalDateTime; import java.util.List;
public record WorkOrderProgressVO(Long id,String content,List<String> imageUrls,Double longitude,Double latitude,String statusAfter,Long createdBy,String createdByName,LocalDateTime createdAt) { }

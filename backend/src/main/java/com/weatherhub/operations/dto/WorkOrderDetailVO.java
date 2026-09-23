package com.weatherhub.operations.dto;
import java.util.List;
public record WorkOrderDetailVO(OpsWorkOrderVO workOrder,List<WorkOrderProgressVO> progress) { }

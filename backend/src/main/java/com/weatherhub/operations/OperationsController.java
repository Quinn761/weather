package com.weatherhub.operations;
import com.weatherhub.common.ApiResponse; import com.weatherhub.operations.dto.*; import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*; import java.util.List;
@RestController @RequestMapping("/api/operations") @RequiredArgsConstructor public class OperationsController {
 private final OperationsService service;
 @GetMapping("/events") public ApiResponse<List<OpsEventVO>> events(@RequestParam(required=false) String status,@RequestParam(required=false) String sourceType){return ApiResponse.ok(service.events(status,sourceType));}
 @PostMapping("/events") public ApiResponse<OpsEventVO> createEvent(@Valid @RequestBody SaveEventRequest request,Authentication auth){return ApiResponse.ok(service.createEvent(request,userId(auth)));}
 @GetMapping("/work-orders") public ApiResponse<List<OpsWorkOrderVO>> orders(@RequestParam(required=false) String status){return ApiResponse.ok(service.workOrders(status));}
 @PostMapping("/work-orders") public ApiResponse<OpsWorkOrderVO> createOrder(@Valid @RequestBody SaveWorkOrderRequest request,Authentication auth){return ApiResponse.ok(service.createWorkOrder(request,userId(auth)));}
 @PatchMapping("/work-orders/{id}/status") public ApiResponse<OpsWorkOrderVO> updateStatus(@PathVariable Long id,@Valid @RequestBody UpdateWorkOrderStatusRequest request,Authentication auth){return ApiResponse.ok(service.updateWorkOrderStatus(id,request,userId(auth)));}
 @PatchMapping("/work-orders/{id}/assignment") public ApiResponse<OpsWorkOrderVO> assign(@PathVariable Long id,@Valid @RequestBody AssignWorkOrderRequest request,Authentication auth){return ApiResponse.ok(service.assignWorkOrder(id,request,userId(auth)));}
 @GetMapping("/work-orders/{id}") public ApiResponse<WorkOrderDetailVO> detail(@PathVariable Long id){return ApiResponse.ok(service.workOrderDetail(id));}
 @PostMapping("/work-orders/{id}/progress") public ApiResponse<WorkOrderProgressVO> progress(@PathVariable Long id,@Valid @RequestBody AddWorkOrderProgressRequest request,Authentication auth){return ApiResponse.ok(service.addProgress(id,request,userId(auth)));}
 @PostMapping("/work-orders/{id}/acceptance") public ApiResponse<OpsWorkOrderVO> accept(@PathVariable Long id,@Valid @RequestBody AcceptWorkOrderRequest request,Authentication auth){return ApiResponse.ok(service.acceptWorkOrder(id,request,userId(auth)));}
 @DeleteMapping("/work-orders/{id}") public ApiResponse<Void> deleteOrder(@PathVariable Long id){service.deleteWorkOrder(id);return ApiResponse.ok();}
 @GetMapping("/notifications") public ApiResponse<List<OpsNotificationVO>> notifications(Authentication auth){return ApiResponse.ok(service.notifications(userId(auth)));}
 @PostMapping("/notifications/{id}/read") public ApiResponse<Void> read(@PathVariable Long id,Authentication auth){service.readNotification(id,userId(auth));return ApiResponse.ok();}
 private static Long userId(Authentication auth){return Long.valueOf(auth.getName());}
}

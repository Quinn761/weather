package com.weatherhub.operations;
import lombok.RequiredArgsConstructor; import lombok.extern.slf4j.Slf4j; import org.springframework.scheduling.annotation.Scheduled; import org.springframework.stereotype.Component;
@Slf4j @Component @RequiredArgsConstructor public class OperationsSyncScheduler {
 private final OperationsService operationsService;
 @Scheduled(initialDelay = 30000, fixedDelay = 300000) public void synchronize() { try { operationsService.syncExternalEvents(); } catch (Exception ex) { log.warn("Operations event synchronization failed: {}", ex.getMessage()); } }
}

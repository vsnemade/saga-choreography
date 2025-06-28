package com.vinsguru.inventory.service;

import com.vinsguru.dto.InventoryDto;
import com.vinsguru.events.inventory.InventoryEvent;
import com.vinsguru.events.inventory.InventoryStatus;
import com.vinsguru.events.order.OrderEvent;
import com.vinsguru.inventory.entity.OrderInventoryConsumption;
import com.vinsguru.inventory.repository.OrderInventoryConsumptionRepository;
import com.vinsguru.inventory.repository.OrderInventoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class InventoryService {

    @Autowired
    private OrderInventoryRepository inventoryRepository;

    @Autowired
    private OrderInventoryConsumptionRepository consumptionRepository;

    @Transactional
    public InventoryEvent newOrderInventory(OrderEvent orderEvent){
        log.info("Processing inventory for new order {}", orderEvent);
        InventoryDto dto = InventoryDto.of(orderEvent.getPurchaseOrder().getOrderId(), orderEvent.getPurchaseOrder().getProductId());
        InventoryEvent inventoryEvent= inventoryRepository.findById(orderEvent.getPurchaseOrder().getProductId())
                .filter(i -> i.getAvailableInventory() > 0 )
                .map(i -> {
                    i.setAvailableInventory(i.getAvailableInventory() - 1);
                    consumptionRepository.save(OrderInventoryConsumption.of(orderEvent.getPurchaseOrder().getOrderId(), orderEvent.getPurchaseOrder().getProductId(), 1));
                    return new InventoryEvent(dto, InventoryStatus.RESERVED);
                })
                .orElse(new InventoryEvent(dto, InventoryStatus.REJECTED));
      log.info("Inventory status for order event {} is {}", orderEvent, inventoryEvent);
      return inventoryEvent;
    }

    @Transactional
    public void cancelOrderInventory(OrderEvent orderEvent){
        log.info("Processing inventory for cancelling order {}", orderEvent);
        consumptionRepository.findById(orderEvent.getPurchaseOrder().getOrderId())
                .ifPresent(ci -> {
                    inventoryRepository.findById(ci.getProductId())
                            .ifPresent(i ->
                                i.setAvailableInventory(i.getAvailableInventory() + ci.getQuantityConsumed())
                            );
                    consumptionRepository.delete(ci);
                    log.info("Inventory consumption is reversed for {}" , orderEvent);
                });
    }

}

package com.vinsguru.payment.service;

import com.vinsguru.dto.PaymentDto;
import com.vinsguru.events.order.OrderEvent;
import com.vinsguru.events.payment.PaymentEvent;
import com.vinsguru.events.payment.PaymentStatus;
import com.vinsguru.payment.entity.UserTransaction;
import com.vinsguru.payment.repository.UserBalanceRepository;
import com.vinsguru.payment.repository.UserTransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class PaymentService {

    @Autowired
    private UserBalanceRepository balanceRepository;

    @Autowired
    private UserTransactionRepository transactionRepository;

    @Transactional
    public PaymentEvent newOrderEvent(OrderEvent orderEvent){
        log.info("Processing payment for new order {}", orderEvent);
        var purchaseOrder = orderEvent.getPurchaseOrder();
        var dto = PaymentDto.of(purchaseOrder.getOrderId(), purchaseOrder.getUserId(), purchaseOrder.getPrice());
        PaymentEvent paymentEvent= this.balanceRepository.findById(purchaseOrder.getUserId())
                .filter(ub -> ub.getBalance() >= purchaseOrder.getPrice())
                .map(ub -> {
                    ub.setBalance(ub.getBalance() - purchaseOrder.getPrice());
                    this.transactionRepository.save(UserTransaction.of(purchaseOrder.getOrderId(), purchaseOrder.getUserId(), purchaseOrder.getPrice()));
                    return new PaymentEvent(dto, PaymentStatus.RESERVED);
                })
                .orElse(new PaymentEvent(dto, PaymentStatus.REJECTED));
        log.info("Payment status for Order event {} is {}", orderEvent, paymentEvent);
        return paymentEvent;
    }

    @Transactional
    public void cancelOrderEvent(OrderEvent orderEvent){
        log.info("Processing payment for cancelling order {}", orderEvent);
        this.transactionRepository.findById(orderEvent.getPurchaseOrder().getOrderId())
                .ifPresent(ut -> {
                    this.transactionRepository.delete(ut);
                    this.balanceRepository.findById(ut.getUserId())
                            .ifPresent(ub -> ub.setBalance(ub.getBalance() + ut.getAmount()));
                    log.info("Balanced reversed for order {}", orderEvent);
                });
    }
}

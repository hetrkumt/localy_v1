package com.localy.payment_service.virtualAcount.dto;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
public class CreateStoreAccountRequest {
    private Long storeId;
    private String ownerUserId; // 가게 주인의 userId
    private BigDecimal initialBalance;
}
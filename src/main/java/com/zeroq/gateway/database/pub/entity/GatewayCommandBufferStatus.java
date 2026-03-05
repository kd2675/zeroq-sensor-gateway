package com.zeroq.gateway.database.pub.entity;

public enum GatewayCommandBufferStatus {
    PENDING_DISPATCH,
    DISPATCHED,
    ACK_PENDING_CLOUD,
    ACKED,
    CANCELED,
    FAILED
}

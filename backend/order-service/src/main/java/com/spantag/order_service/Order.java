package com.spantag.order_service;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Username from JWT — injected by gateway as X-Auth-Username
    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    // Filled in once payment service processes the transaction
    private String paymentId;

    // Reason stored when order is cancelled
    private String cancellationReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // ── Legacy columns from old schema ────────────────────────────────────
    // These columns exist in the DB from a previous version of this service.
    // Declaring them here (as nullable) ensures:
    //   1. Hibernate includes them in its schema model → no INSERT failure
    //   2. ddl-auto: update won't try to re-create or alter them on startup
    //   3. No manual ALTER TABLE SQL is needed when DB is recreated from scratch
    //
    // They are not used by the current business logic and always stay null.

    @Column(name = "shipping_address_id")
    private String shippingAddressId;          // legacy — always null

    @Column(name = "total_amount", precision = 12, scale = 2)
    private BigDecimal totalAmount;            // legacy — always null

    @Column(name = "user_id")
    private String userId;                     // legacy — always null

    // ─────────────────────────────────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters & Setters
    public Long getId()                        { return id; }
    public void setId(Long id)                 { this.id = id; }

    public String getUsername()                { return username; }
    public void setUsername(String v)          { this.username = v; }

    public String getProductName()             { return productName; }
    public void setProductName(String v)       { this.productName = v; }

    public Integer getQuantity()               { return quantity; }
    public void setQuantity(Integer v)         { this.quantity = v; }

    public BigDecimal getAmount()              { return amount; }
    public void setAmount(BigDecimal v)        { this.amount = v; }

    public OrderStatus getStatus()             { return status; }
    public void setStatus(OrderStatus v)       { this.status = v; }

    public String getPaymentId()               { return paymentId; }
    public void setPaymentId(String v)         { this.paymentId = v; }

    public String getCancellationReason()      { return cancellationReason; }
    public void setCancellationReason(String v){ this.cancellationReason = v; }

    public LocalDateTime getCreatedAt()        { return createdAt; }
    public LocalDateTime getUpdatedAt()        { return updatedAt; }

    // Legacy getters/setters (not used by business logic)
    public String getShippingAddressId()       { return shippingAddressId; }
    public void setShippingAddressId(String v) { this.shippingAddressId = v; }

    public BigDecimal getTotalAmount()         { return totalAmount; }
    public void setTotalAmount(BigDecimal v)   { this.totalAmount = v; }

    public String getUserId()                  { return userId; }
    public void setUserId(String v)            { this.userId = v; }
}


//package com.spantag.order_service;
//
//import jakarta.persistence.*;
//import java.math.BigDecimal;
//import java.time.LocalDateTime;
//
//@Entity
//@Table(name = "orders")
//public class Order {
//
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private Long id;
//
//    // Username from JWT — injected by gateway as X-Auth-Username
//    @Column(nullable = false)
//    private String username;
//
//    @Column(nullable = false)
//    private String productName;
//
//    @Column(nullable = false)
//    private Integer quantity;
//
//    @Column(nullable = false, precision = 10, scale = 2)
//    private BigDecimal amount;
//
//    @Enumerated(EnumType.STRING)
//    @Column(nullable = false)
//    private OrderStatus status;
//
//    // Filled in once payment service processes the transaction
//    private String paymentId;
//
//    // Reason stored when order is cancelled
//    private String cancellationReason;
//
//    @Column(nullable = false)
//    private LocalDateTime createdAt;
//
//    private LocalDateTime updatedAt;
//
//    @PrePersist
//    protected void onCreate() {
//        createdAt = LocalDateTime.now();
//        updatedAt = LocalDateTime.now();
//    }
//
//    @PreUpdate
//    protected void onUpdate() {
//        updatedAt = LocalDateTime.now();
//    }
//
//    // Getters & Setters
//    public Long getId()                        { return id; }
//    public void setId(Long id)                 { this.id = id; }
//
//    public String getUsername()                { return username; }
//    public void setUsername(String v)          { this.username = v; }
//
//    public String getProductName()             { return productName; }
//    public void setProductName(String v)       { this.productName = v; }
//
//    public Integer getQuantity()               { return quantity; }
//    public void setQuantity(Integer v)         { this.quantity = v; }
//
//    public BigDecimal getAmount()              { return amount; }
//    public void setAmount(BigDecimal v)        { this.amount = v; }
//
//    public OrderStatus getStatus()             { return status; }
//    public void setStatus(OrderStatus v)       { this.status = v; }
//
//    public String getPaymentId()               { return paymentId; }
//    public void setPaymentId(String v)         { this.paymentId = v; }
//
//    public String getCancellationReason()          { return cancellationReason; }
//    public void setCancellationReason(String v)    { this.cancellationReason = v; }
//
//    public LocalDateTime getCreatedAt()        { return createdAt; }
//    public LocalDateTime getUpdatedAt()        { return updatedAt; }
//}
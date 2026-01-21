package org.pojoquery.integrationtest.orders;

import java.time.LocalDateTime;

import org.pojoquery.annotations.GenerateQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;

/**
 * CustomerOrder entity - represents a customer's order.
 * Named CustomerOrder (not Order) to avoid SQL reserved word conflicts.
 */
@Table("customer_orders")
@GenerateQuery
public class CustomerOrder {

    @Id
    private Long id;

    private LocalDateTime lastUpdate;
    private OrderStatus status;
    private Integer discount;
    private String shipmentInfo;

    public CustomerOrder() {
    }

    public CustomerOrder(OrderStatus status, Integer discount) {
        this.status = status;
        this.discount = discount;
        this.lastUpdate = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(LocalDateTime lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public Integer getDiscount() {
        return discount;
    }

    public void setDiscount(Integer discount) {
        this.discount = discount;
    }

    public String getShipmentInfo() {
        return shipmentInfo;
    }

    public void setShipmentInfo(String shipmentInfo) {
        this.shipmentInfo = shipmentInfo;
    }

    @Override
    public String toString() {
        return "CustomerOrder{id=" + id + ", status='" + status + 
               "', discount=" + discount + "%, lastUpdate=" + lastUpdate + "}";
    }
}

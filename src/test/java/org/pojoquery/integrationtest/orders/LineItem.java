package org.pojoquery.integrationtest.orders;

import org.pojoquery.annotations.GenerateQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;

/**
 * LineItem entity - represents an item in a customer order.
 * Links CustomerOrder to VendorPart with quantity.
 */
@Table("line_items")
@GenerateQuery
public class LineItem {

    @Id
    private Long id;
    
    private CustomerOrder customerOrder;
    private Integer itemId;  // Line item number within the order
    private Integer quantity;

    private VendorPart vendorPart;

    protected LineItem() {
    }

    public LineItem(CustomerOrder order, Integer itemId, Integer quantity, VendorPart vendorPart) {
        this.customerOrder = order;
        this.itemId = itemId;
        this.quantity = quantity;
        this.vendorPart = vendorPart;
    }

    public Long getId() {
        return id;
    }

    public Integer getItemId() {
        return itemId;
    }

    public void setItemId(Integer itemId) {
        this.itemId = itemId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public CustomerOrder getCustomerOrder() {
        return customerOrder;
    }

    public void setCustomerOrder(CustomerOrder customerOrder) {
        this.customerOrder = customerOrder;
    }

    public VendorPart getVendorPart() {
        return vendorPart;
    }

    public void setVendorPart(VendorPart vendorPart) {
        this.vendorPart = vendorPart;
    }

    @Override
    public String toString() {
        return "LineItem{id=" + id + ", customerOrder=" + customerOrder + ", itemId=" + itemId +
               ", quantity=" + quantity + ", vendorPart=" + vendorPart + "}";
    }
}

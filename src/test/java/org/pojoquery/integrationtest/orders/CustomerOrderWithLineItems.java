package org.pojoquery.integrationtest.orders;

import java.math.BigDecimal;
import java.util.List;

import org.pojoquery.annotations.GenerateQuery;
import org.pojoquery.annotations.Link;

@GenerateQuery
public class CustomerOrderWithLineItems extends CustomerOrder {

    @Link(foreignlinkfield = "customerOrder_id")
	private List<LineItem> lineItems;

    public List<LineItem> getLineItems() {
        return lineItems;
    }

    public void setLineItems(List<LineItem> lineItems) {
        this.lineItems = lineItems;
    }

    /**
     * Calculate total price of all line items with discount applied.
     */
    public BigDecimal getTotalPrice() {
        if (lineItems == null || lineItems.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = lineItems.stream()
                .map(item -> item.getVendorPart().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
		Integer discount = getDiscount();
        if (discount != null && discount > 0) {
            BigDecimal discountMultiplier = BigDecimal.ONE.subtract(
                    BigDecimal.valueOf(discount).divide(BigDecimal.valueOf(100)));
            total = total.multiply(discountMultiplier);
        }
        return total;
    }

}

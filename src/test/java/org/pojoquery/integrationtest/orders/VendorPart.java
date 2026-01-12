package org.pojoquery.integrationtest.orders;

import org.pojoquery.annotations.GenerateQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;

import java.math.BigDecimal;

/**
 * VendorPart entity - links a Part to a Vendor with pricing information.
 * Each VendorPart represents a specific part supplied by a specific vendor.
 */
@Table("vendor_parts")
@GenerateQuery
public class VendorPart {

    @Id
    private Long id;

    private String description;
    private BigDecimal price;
    private Part part;

    private Vendor vendor;

    public VendorPart() {
    }

    public VendorPart(String description, BigDecimal price, Part part, Vendor vendor) {
        this.description = description;
        this.price = price;
        this.part = part;
        this.vendor = vendor;
    }

    public Long getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Part getPart() {
        return part;
    }

    public void setPart(Part part) {
        this.part = part;
    }

    public Vendor getVendor() {
        return vendor;
    }

    public void setVendor(Vendor vendor) {
        this.vendor = vendor;
    }

    @Override
    public String toString() {
        return "VendorPart{id=" + id + ", description='" + description + "', price=" + price + "}";
    }
}

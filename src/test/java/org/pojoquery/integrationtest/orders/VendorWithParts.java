package org.pojoquery.integrationtest.orders;

import java.util.List;

import org.pojoquery.annotations.GenerateQuery;

@GenerateQuery
public class VendorWithParts extends Vendor {
	private List<VendorPart> vendorParts;

	public List<VendorPart> getVendorParts() {
		return vendorParts;
	}

	public void setVendorParts(List<VendorPart> vendorParts) {
		this.vendorParts = vendorParts;
	}

}

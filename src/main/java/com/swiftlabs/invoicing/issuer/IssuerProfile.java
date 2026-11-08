package com.swiftlabs.invoicing.issuer;

/**
 * What every page needs of the issuer (header, mentions): its legal status and its own TVA mention, read without
 * the rest of the row (its logo is up to 500 KB).
 */
public record IssuerProfile(CompanyType companyType, String vatMention) {
}

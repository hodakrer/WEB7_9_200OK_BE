package com.windfall.api.payment.reconcilebatch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentInquiryResponse(
    String paymentKey,
    String status
) {
}
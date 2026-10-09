package com.occupify.gateway.dto.base;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"statusCode", "message", "errorCode"})
public record ErrorResponse(Integer statusCode, String message, String errorCode) {
}

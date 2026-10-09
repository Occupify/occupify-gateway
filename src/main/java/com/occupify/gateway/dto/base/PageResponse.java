package com.occupify.gateway.dto.base;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"statusCode", "message", "data", "pagination"})
public record PageResponse<T>(Integer statusCode, String message, List<T> data, PagingInfo pagination) {

    public record PagingInfo(int page, int size, long totalElements, int totalPages) {
    }
}

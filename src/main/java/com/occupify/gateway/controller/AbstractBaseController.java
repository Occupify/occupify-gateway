package com.occupify.gateway.controller;

import com.occupify.gateway.dto.base.CreatedResponse;
import com.occupify.gateway.dto.base.ErrorResponse;
import com.occupify.gateway.dto.base.PageResponse;
import com.occupify.gateway.dto.base.SingleResponse;
import com.occupify.gateway.dto.base.SuccessResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

public abstract class AbstractBaseController {

    @Autowired(required = false)
    protected ResponseFactory responseFactory = new ResponseFactory();

    protected <T> SingleResponse<T> successSingle(T data, String message) {
        return responseFactory.createSingleResponse(HttpStatus.OK, message, data);
    }

    protected SuccessResponse success(String message) {
        return responseFactory.createSuccessResponse(HttpStatus.OK, message);
    }

    protected <T> CreatedResponse<T> created(T data, String message) {
        return responseFactory.createCreatedResponse(HttpStatus.CREATED, message, data);
    }

    protected <T> PageResponse<T> paging(List<T> data, int page, int size, long totalElements, int totalPages, String message) {
        return responseFactory.createPageResponse(HttpStatus.OK, message, data, page, size, totalElements, totalPages);
    }

    protected <T> ResponseEntity<SingleResponse<T>> successSingleEntity(T data, String message) {
        return responseFactory.successSingle(data, message);
    }

    protected ResponseEntity<SuccessResponse> successEntity(String message) {
        return responseFactory.success(message);
    }

    protected <T> ResponseEntity<CreatedResponse<T>> createdEntity(T data, String message) {
        return responseFactory.created(data, message);
    }

    protected ResponseEntity<ErrorResponse> errorEntity(HttpStatus status, String message, String errorCode) {
        return responseFactory.error(status, message, errorCode);
    }
}

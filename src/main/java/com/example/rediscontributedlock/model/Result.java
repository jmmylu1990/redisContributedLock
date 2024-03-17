package com.example.rediscontributedlock.model;

import lombok.Data;

/**
 * @author vanliou
 */
public @Data class Result<T> {

    private T data;

    private Boolean result;

    private String msg;


}

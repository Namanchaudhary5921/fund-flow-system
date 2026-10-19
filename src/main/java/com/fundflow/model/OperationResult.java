package com.fundflow.model;

public record OperationResult(boolean success, String message) {
    public static OperationResult ok(String msg)   { return new OperationResult(true, msg); }
    public static OperationResult fail(String msg) { return new OperationResult(false, msg); }
}

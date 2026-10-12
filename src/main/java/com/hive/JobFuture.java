package com.hive;

import java.util.concurrent.CompletableFuture;

final class JobFuture<T> extends CompletableFuture<T> {

    final CompletableFuture<T> termination = new CompletableFuture<>();
}

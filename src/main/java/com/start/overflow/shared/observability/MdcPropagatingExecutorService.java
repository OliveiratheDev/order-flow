package com.start.overflow.shared.observability;

import org.slf4j.MDC;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public final class MdcPropagatingExecutorService extends AbstractExecutorService {
    private final ExecutorService delegate;

    public MdcPropagatingExecutorService(ExecutorService delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        Map<String, String> callerContext = MDC.getCopyOfContextMap();
        delegate.execute(() -> run(command, callerContext));
    }

    private void run(Runnable command, Map<String, String> callerContext) {
        Map<String, String> workerContext = MDC.getCopyOfContextMap();
        replaceContext(callerContext);
        try {
            command.run();
        } finally {
            replaceContext(workerContext);
        }
    }

    private void replaceContext(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}

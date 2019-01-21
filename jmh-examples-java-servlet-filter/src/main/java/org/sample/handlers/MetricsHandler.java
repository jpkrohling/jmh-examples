package org.sample.handlers;


import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

import java.util.Date;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * Handler that records some metrics
 *
 * @author Stuart Douglas
 */
// Based on
// https://github.com/undertow-io/undertow/blob/master/core/src/main/java/io/undertow/server/handlers/MetricsHandler.java
public class MetricsHandler implements HttpHandler {

    public static final HandlerWrapper WRAPPER = new HandlerWrapper() {
        @Override
        public HttpHandler wrap(HttpHandler handler) {
            return new MetricsHandler(handler);
        }
    };

    private volatile MetricResult totalResult = new MetricResult(new Date());
    private final HttpHandler next;

    public MetricsHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if(!exchange.isComplete()) {
            final long start = System.currentTimeMillis();
            exchange.addExchangeCompleteListener(new ExchangeCompletionListener() {
                @Override
                public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
                    long time = System.currentTimeMillis() - start;
                    totalResult.update((int) time, exchange.getStatusCode());
                    System.out.println("Total result : " + totalResult.toString());
                    nextListener.proceed();
                }
            });
        }
        next.handleRequest(exchange);
    }

    public void reset() {
        this.totalResult = new MetricResult(new Date());
    }

    public MetricResult getMetrics() {
        return new MetricResult(this.totalResult);
    }

    public static class MetricResult {

        private static final AtomicLongFieldUpdater<MetricResult> totalRequestTimeUpdater = AtomicLongFieldUpdater.newUpdater(MetricResult.class, "totalRequestTime");
        private static final AtomicIntegerFieldUpdater<MetricResult> maxRequestTimeUpdater = AtomicIntegerFieldUpdater.newUpdater(MetricResult.class, "maxRequestTime");
        private static final AtomicIntegerFieldUpdater<MetricResult> minRequestTimeUpdater = AtomicIntegerFieldUpdater.newUpdater(MetricResult.class, "minRequestTime");
        private static final AtomicLongFieldUpdater<MetricResult> invocationsUpdater = AtomicLongFieldUpdater.newUpdater(MetricResult.class, "totalRequests");
        private static final AtomicLongFieldUpdater<MetricResult> errorsUpdater = AtomicLongFieldUpdater.newUpdater(MetricResult.class, "totalErrors");

        private final Date metricsStartDate;

        private volatile long totalRequestTime;
        private volatile int maxRequestTime;
        private volatile int minRequestTime = -1;
        private volatile long totalRequests;
        private volatile long totalErrors;

        public MetricResult(Date metricsStartDate) {
            this.metricsStartDate = metricsStartDate;
        }

        public MetricResult(MetricResult copy) {
            this.metricsStartDate = copy.metricsStartDate;
            this.totalRequestTime = copy.totalRequestTime;
            this.maxRequestTime = copy.maxRequestTime;
            this.minRequestTime = copy.minRequestTime;
            this.totalRequests = copy.totalRequests;
            this.totalErrors = copy.totalErrors;
        }

        void update(final int requestTime, int statusCode) {
            totalRequestTimeUpdater.addAndGet(this, requestTime);
            int maxRequestTime;
            do {
                maxRequestTime = this.maxRequestTime;
                if (requestTime < maxRequestTime) {
                    break;
                }
            } while (!maxRequestTimeUpdater.compareAndSet(this, maxRequestTime, requestTime));

            int minRequestTime;
            do {
                minRequestTime = this.minRequestTime;
                if (requestTime > minRequestTime && minRequestTime != -1) {
                    break;
                }
            } while (!minRequestTimeUpdater.compareAndSet(this, minRequestTime, requestTime));
            invocationsUpdater.incrementAndGet(this);
            if(statusCode >= 400) {
                errorsUpdater.incrementAndGet(this);
            }


        }

        public Date getMetricsStartDate() {
            return metricsStartDate;
        }

        public long getTotalRequestTime() {
            return totalRequestTime;
        }

        public int getMaxRequestTime() {
            return maxRequestTime;
        }

        public int getMinRequestTime() {
            return minRequestTime;
        }

        public long getTotalRequests() {
            return totalRequests;
        }

        public long getTotalErrors() {
            return totalErrors;
        }

        @Override
        public String toString() {
            return "MetricResult{" +
                    "metricsStartDate=" + metricsStartDate +
                    ", totalRequestTime=" + totalRequestTime +
                    ", maxRequestTime=" + maxRequestTime +
                    ", minRequestTime=" + minRequestTime +
                    ", totalRequests=" + totalRequests +
                    ", totalErrors=" + totalErrors +
                    '}';
        }
    }
}
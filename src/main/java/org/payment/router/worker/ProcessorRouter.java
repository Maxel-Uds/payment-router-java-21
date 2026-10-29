package org.payment.router.worker;

import jakarta.inject.Singleton;

import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class ProcessorRouter {
    // A checagem de consistência compara até now-2s: um processor mais lento que isso confirma pagamentos
    // que ainda não salvamos no instante da checagem.
    private static final long SLOW_THRESHOLD_MS = 1500;
    private static final long PROBE_INTERVAL_MS = 200;
    private static final long FALLBACK_COOLDOWN_MS = 1000;

    public enum Route { DEFAULT, DEFAULT_PROBE, FALLBACK, WAIT }

    private final AtomicBoolean probing = new AtomicBoolean();
    private volatile boolean defaultFailing;
    private volatile boolean defaultSlow;
    private volatile long nextProbeAt;
    private volatile long fallbackAvailableAt;

    public Route next() {
        if (!defaultFailing && !defaultSlow) {
            return Route.DEFAULT;
        }

        long now = System.currentTimeMillis();
        if (now >= nextProbeAt && probing.compareAndSet(false, true)) {
            return Route.DEFAULT_PROBE;
        }

        // Com o default fora, segurar o pagamento sai mais barato que o fallback: o backlog drena quando ele volta.
        if (defaultFailing) {
            return Route.WAIT;
        }

        return now >= fallbackAvailableAt ? Route.FALLBACK : Route.DEFAULT;
    }

    public void defaultProcessed(long latencyMs) {
        defaultFailing = false;
        defaultSlow = latencyMs > SLOW_THRESHOLD_MS;
    }

    public void defaultFailed() {
        defaultFailing = true;
    }

    public void probeFinished() {
        nextProbeAt = System.currentTimeMillis() + PROBE_INTERVAL_MS;
        probing.set(false);
    }

    public void fallbackFailed() {
        fallbackAvailableAt = System.currentTimeMillis() + FALLBACK_COOLDOWN_MS;
    }
}

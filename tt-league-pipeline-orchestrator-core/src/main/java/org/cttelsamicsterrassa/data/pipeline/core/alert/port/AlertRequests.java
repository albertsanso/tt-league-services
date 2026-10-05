package org.cttelsamicsterrassa.data.pipeline.core.alert.port;

/** Asks for an alert evaluation without waiting for it; implementations coalesce requests and never block or throw. */
public interface AlertRequests {

    void request();
}

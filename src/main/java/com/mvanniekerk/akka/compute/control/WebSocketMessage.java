package com.mvanniekerk.akka.compute.control;

import com.mvanniekerk.akka.compute.vertex.CoreLog;

public interface WebSocketMessage {
    record Log(String type, CoreLog.LogMessage content) implements WebSocketMessage {}
    record MetricsAggregate(String type, Control.VertexMetricsAggregator content) implements WebSocketMessage {}
}

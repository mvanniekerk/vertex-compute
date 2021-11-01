package com.mvanniekerk.akka.compute.vertex;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.mvanniekerk.akka.compute.compute.Compiler;
import com.mvanniekerk.akka.compute.compute.ComputeCore;
import com.mvanniekerk.akka.compute.compute.StaticClassNameCompiler;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;

public class Core extends AbstractBehavior<VertexMessage> {
    private static final int MAX_LOG_MESSAGES = 100;
    private static final int ROLLING_AVERAGE_DURATION_SECONDS = 10;

    public static Behavior<VertexMessage> create(String id, String name, String code) {
        return Behaviors.setup(context ->
                Behaviors.withTimers(scheduler -> new Core(context, scheduler, id, name, code)));
    }

    private final Compiler compiler = new StaticClassNameCompiler();
    private final TimerScheduler<VertexMessage> scheduler;
    private final String id;

    private final Map<String, ActorRef<? super CoreConsumer>> targetsById = new HashMap<>();
    private final Map<String, Runnable> periodicRunnableByKey = new HashMap<>();
    private final Queue<CoreLog.LogMessage> log = new ArrayDeque<>();
    private final Queue<Long> messageReceiveTimestamps = new ArrayDeque<>();

    private String name;
    private String code;
    private ComputeCore process;
    private ActorRef<CoreLog.LogMessage> logSubscriber;
    private long messagesSent = 0;
    private long messagesReceived = 0;

    private Core(ActorContext<VertexMessage> context, TimerScheduler<VertexMessage> scheduler, String id, String name, String code) {
        super(context);
        this.scheduler = scheduler;
        this.id = id;
        this.name = name;
        this.code = code;
        process = compiler.compile(code).apply(this);
    }

    public void send(JsonNode message) {
        var now = System.currentTimeMillis();
        removeOldMessageReceiveTimestamps(now);
        messageReceiveTimestamps.add(now);
        messagesSent++;

        targetsById.forEach((id, target) -> target.tell(new CoreConsumer.Message(message)));
    }

    public void log(String message) {
        CoreLog.LogMessage logMessage = new CoreLog.LogMessage(ZonedDateTime.now(), id, message);
        if (log.size() == MAX_LOG_MESSAGES) {
            log.poll();
        }
        log.add(logMessage);
        if (logSubscriber != null) {
            logSubscriber.tell(logMessage);
        }
    }

    public void schedulePeriodic(String key, Duration interval, Runnable runnable) {
        if (periodicRunnableByKey.containsKey(key)) {
            stopPeriodic(key);
        }
        periodicRunnableByKey.put(key, runnable);
        scheduler.startTimerAtFixedRate(key, new CoreConsumer.Tick(key), Duration.ofMillis(0), interval);
    }

    public void scheduleOnce(String key, Duration delay, Runnable runnable) {
        if (periodicRunnableByKey.containsKey(key)) {
            stopPeriodic(key);
        }
        periodicRunnableByKey.put(key, runnable);
        scheduler.startSingleTimer(key, new CoreConsumer.Tick(key), delay);
    }

    public void stopPeriodic(String key) {
        scheduler.cancel(key);
        periodicRunnableByKey.remove(key);
    }

    public VertexDescription describe() {
        return new VertexDescription(id, name, code);
    }

    @Override
    public Receive<VertexMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(CoreConsumer.Message.class, msg -> {
                    messagesReceived++;
                    JsonNode body = msg.body();
                    if (process != null) {
                        process.receive(body);
                    }
                    return this;
                })
                .onMessage(CoreConsumer.Tick.class, msg -> {
                    Runnable runnable = periodicRunnableByKey.get(msg.key());
                    if (runnable != null) {
                        runnable.run();
                    }
                    return this;
                })

                .onMessage(CoreLog.SubscribeLog.class, msg -> {
                    logSubscriber = msg.subscriber();
                    for (CoreLog.LogMessage logMessage : log) {
                        logSubscriber.tell(logMessage);
                    }
                    return this;
                })
                .onMessage(CoreLog.UnsubscribeLog.class, msg -> {
                    logSubscriber = null;
                    return this;
                })

                .onMessage(CoreMetrics.GetMetrics.class, msg -> {
                    var now = System.currentTimeMillis();
                    removeOldMessageReceiveTimestamps(now);
                    var avgMessages = 1.0 * messageReceiveTimestamps.size() / ROLLING_AVERAGE_DURATION_SECONDS;
                    var metrics = new CoreMetrics.Metrics(avgMessages, messagesReceived, messagesSent);
                    msg.replyTo().tell(new CoreMetrics.MetricsWithId(id, metrics));
                    return this;
                })

                .onMessage(CoreControl.Describe.class, msg -> {
                    msg.replyTo().tell(describe());
                    return this;
                })
                .onMessage(CoreControl.ShowCode.class, msg -> {
                    msg.replyTo().tell(code);
                    return this;
                })
                .onMessage(CoreControl.LoadCode.class, msg -> {
                    periodicRunnableByKey.keySet().forEach(scheduler::cancel);
                    periodicRunnableByKey.clear();
                    log.clear();
                    messageReceiveTimestamps.clear();
                    messagesSent = 0;
                    messagesReceived = 0;
                    code = msg.code();
                    process = compiler.compile(code).apply(this);
                    msg.replyTo().tell(describe());
                    return this;
                })
                .onMessage(CoreControl.LoadName.class, msg -> {
                    this.name = msg.name();
                    msg.replyTo().tell(describe());
                    return this;
                })
                .onMessage(CoreControl.Connect.class, msg -> {
                    targetsById.put(msg.id(), msg.target());
                    return this;
                })
                .build();
    }

    private void removeOldMessageReceiveTimestamps(long now) {
        for (Iterator<Long> iterator = messageReceiveTimestamps.iterator(); iterator.hasNext(); ) {
            Long timestamp = iterator.next();
            if (timestamp < now - ROLLING_AVERAGE_DURATION_SECONDS * 1000) {
                iterator.remove();
            } else {
                break;
            }
        }
    }
}

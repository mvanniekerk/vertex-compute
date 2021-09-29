package com.mvanniekerk.akka.compute.vertex;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.mvanniekerk.akka.compute.compute.Compiler;
import com.mvanniekerk.akka.compute.compute.ComputeCore;
import com.mvanniekerk.akka.compute.compute.NoopCompute;
import com.mvanniekerk.akka.compute.compute.StaticClassNameCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;

public class Core extends AbstractBehavior<VertexMessage> {
    private static final Logger LOGGER = LoggerFactory.getLogger(Core.class);
    private static final int MAX_LOG_MESSAGES = 100;

    private final Compiler compiler = new StaticClassNameCompiler();
    private final TimerScheduler<VertexMessage> scheduler;
    private final String id;
    private final String name;

    private final Map<String, ActorRef<? super CoreConsumer>> targetsById = new HashMap<>();
    private final Map<String, Runnable> periodicRunnableByKey = new HashMap<>();
    private final Queue<CoreLog.LogMessage> log = new ArrayDeque<>();
    private String code;
    private ComputeCore process;
    private ActorRef<CoreLog.LogMessage> logSubscriber;

    private Core(ActorContext<VertexMessage> context, TimerScheduler<VertexMessage> scheduler, String id, String name, String code) {
        super(context);
        this.scheduler = scheduler;
        this.id = id;
        this.name = name;
        this.code = code;
        process = compiler.compile(code).apply(this);
    }

    public static Behavior<VertexMessage> create(String id, String name, String code) {
        return Behaviors.setup(context ->
                Behaviors.withTimers(scheduler -> new Core(context, scheduler, id, name, code)));
    }

    public void send(JsonNode message) {
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
        scheduler.startTimerAtFixedRate(key, new CoreConsumer.Tick(key), interval);
    }

    public void stopPeriodic(String key) {
        scheduler.cancel(key);
        periodicRunnableByKey.remove(key);
    }

    @Override
    public Receive<VertexMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(CoreConsumer.Message.class, msg -> {
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

                .onMessage(CoreControl.Describe.class, msg -> {
                    var description = new VertexDescription(id, name, code);
                    msg.replyTo().tell(description);
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
                    code = msg.code();
                    process = compiler.compile(code).apply(this);
                    return this;
                })
                .onMessage(CoreControl.Connect.class, msg -> {
                    targetsById.put(msg.id(), msg.target());
                    return this;
                })
                .build();
    }
}

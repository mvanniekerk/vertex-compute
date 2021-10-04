package com.mvanniekerk.akka.compute.util;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class Aggregator<Reply, Aggregate> extends AbstractBehavior<Aggregator.Message> {

    interface Message {}
    private record ReceiveTimeout() implements Message {}
    private record WrappedReply<T>(T reply) implements Message {}

    public static <R, A> Behavior<Message> create(Class<R> replyClass, Consumer<ActorRef<R>> sendRequests,
                                                  int expectedReplies, ActorRef<A> replyTo,
                                                  Function<List<R>, A> aggregateReplies, Duration timeout) {
        if (expectedReplies == 0) {
            var result = aggregateReplies.apply(Collections.emptyList());
            replyTo.tell(result);
            return Behaviors.stopped();
        }
        return Behaviors.setup(context ->
                new Aggregator<>(replyClass, context, sendRequests, expectedReplies, replyTo, aggregateReplies,
                        timeout));
    }

    private final int expectedReplies;
    private final ActorRef<Aggregate> replyTo;
    private final Function<List<Reply>, Aggregate> aggregateReplies;
    private final List<Reply> replies = new ArrayList<>();

    private Aggregator(Class<Reply> replyClass, ActorContext<Message> context, Consumer<ActorRef<Reply>> sendRequests,
                       int expectedReplies, ActorRef<Aggregate> replyTo,
                       Function<List<Reply>, Aggregate> aggregateReplies, Duration timeout) {
        super(context);
        this.expectedReplies = expectedReplies;
        this.replyTo = replyTo;
        this.aggregateReplies = aggregateReplies;

        context.setReceiveTimeout(timeout, new ReceiveTimeout());

        ActorRef<Reply> replyAdapter = context.messageAdapter(replyClass, WrappedReply::new);
        sendRequests.accept(replyAdapter);
    }

    @Override
    public Receive<Message> createReceive() {
        return newReceiveBuilder()
                .onMessage(WrappedReply.class, this::onReply)
                .onMessage(ReceiveTimeout.class, notUsed -> onReceiveTimeout())
                .build();
    }

    private Behavior<Message> onReply(WrappedReply<Reply> wrappedReply) {
        Reply reply = wrappedReply.reply;
        replies.add(reply);
        if (replies.size() == expectedReplies) {
            Aggregate result = aggregateReplies.apply(replies);
            replyTo.tell(result);
            return Behaviors.stopped();
        } else {
            return this;
        }
    }

    private Behavior<Message> onReceiveTimeout() {
        Aggregate result = aggregateReplies.apply(replies);
        replyTo.tell(result);
        return Behaviors.stopped();
    }
}

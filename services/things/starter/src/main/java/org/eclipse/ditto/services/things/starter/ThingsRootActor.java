/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.things.starter;

import static akka.http.javadsl.server.Directives.logRequest;
import static akka.http.javadsl.server.Directives.logResult;
import static org.eclipse.ditto.services.models.things.ThingsMessagingConstants.CLUSTER_ROLE;

import java.net.ConnectException;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.services.base.config.HealthConfigReader;
import org.eclipse.ditto.services.base.config.HttpConfigReader;
import org.eclipse.ditto.services.base.config.ServiceConfigReader;
import org.eclipse.ditto.services.models.things.ThingsMessagingConstants;
import org.eclipse.ditto.services.things.persistence.actors.ThingNamespaceOpsActor;
import org.eclipse.ditto.services.things.persistence.actors.ThingSupervisorActor;
import org.eclipse.ditto.services.things.persistence.actors.ThingsPersistenceStreamingActorCreator;
import org.eclipse.ditto.services.things.persistence.snapshotting.ThingSnapshotter;
import org.eclipse.ditto.services.things.starter.util.ConfigKeys;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cluster.ClusterStatusSupplier;
import org.eclipse.ditto.services.utils.cluster.ClusterUtil;
import org.eclipse.ditto.services.utils.cluster.RetrieveStatisticsDetailsResponseSupplier;
import org.eclipse.ditto.services.utils.cluster.ShardRegionExtractor;
import org.eclipse.ditto.services.utils.config.ConfigUtil;
import org.eclipse.ditto.services.utils.health.DefaultHealthCheckingActorFactory;
import org.eclipse.ditto.services.utils.health.HealthCheckingActorOptions;
import org.eclipse.ditto.services.utils.health.routes.StatusRoute;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoHealthChecker;
import org.eclipse.ditto.signals.commands.devops.RetrieveStatisticsDetails;

import com.typesafe.config.Config;

import akka.Done;
import akka.actor.AbstractActor;
import akka.actor.ActorKilledException;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.CoordinatedShutdown;
import akka.actor.InvalidActorNameException;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.Status;
import akka.actor.SupervisorStrategy;
import akka.cluster.Cluster;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.event.DiagnosticLoggingAdapter;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.server.Route;
import akka.japi.Creator;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.AskTimeoutException;
import akka.pattern.PatternsCS;
import akka.stream.ActorMaterializer;

/**
 * Our "Parent" Actor which takes care of supervision of all other Actors in our system.
 */
final class ThingsRootActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME = "thingsRoot";

    private static final String RESTARTING_CHILD_MESSAGE = "Restarting child ...";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final SupervisorStrategy strategy = new OneForOneStrategy(true, DeciderBuilder
            .match(NullPointerException.class, e ->
            {
                log.error(e, "NullPointer in child actor: {}", e.getMessage());
                log.info(RESTARTING_CHILD_MESSAGE);
                return SupervisorStrategy.restart();
            }).match(IllegalArgumentException.class, e ->
            {
                log.warning("Illegal Argument in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(IndexOutOfBoundsException.class, e -> {

                log.warning("IndexOutOfBounds in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(IllegalStateException.class, e ->
            {
                log.warning("Illegal State in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(NoSuchElementException.class, e ->
            {
                log.warning("NoSuchElement in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(AskTimeoutException.class, e ->
            {
                log.warning("AskTimeoutException in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(ConnectException.class, e ->
            {
                log.warning("ConnectException in child actor: {}", e.getMessage());
                log.info(RESTARTING_CHILD_MESSAGE);
                return SupervisorStrategy.restart();
            }).match(InvalidActorNameException.class, e ->
            {
                log.warning("InvalidActorNameException in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(ActorKilledException.class, e ->
            {
                log.error(e, "ActorKilledException in child actor: {}", e.message());
                log.info(RESTARTING_CHILD_MESSAGE);
                return SupervisorStrategy.restart();
            }).match(DittoRuntimeException.class, e ->
            {
                log.error(e,
                        "DittoRuntimeException <{}> should not be escalated to ThingsRootActor. Simply resuming Actor.",
                        e.getErrorCode());
                return SupervisorStrategy.resume();
            }).match(Throwable.class, e ->
            {
                log.error(e, "Escalating above root actor!");
                return SupervisorStrategy.escalate();
            }).matchAny(e ->
            {
                log.error("Unknown message:'{}'! Escalating above root actor!", e);
                return SupervisorStrategy.escalate();
            }).build());

    private final RetrieveStatisticsDetailsResponseSupplier retrieveStatisticsDetailsResponseSupplier;

    private ThingsRootActor(final ServiceConfigReader configReader,
            final ActorRef pubSubMediator,
            final ActorMaterializer materializer,
            final ThingSnapshotter.Create thingSnapshotterCreate) {

        final int numberOfShards = configReader.cluster().numberOfShards();
        final Config config = configReader.getRawConfig();

        final Props thingSupervisorProps = getThingSupervisorActorProps(config, pubSubMediator, thingSnapshotterCreate);

        final ClusterShardingSettings shardingSettings =
                ClusterShardingSettings.create(getContext().system())
                        .withRole(CLUSTER_ROLE);

        final ActorRef thingsShardRegion = ClusterSharding.get(getContext().system())
                .start(ThingsMessagingConstants.SHARD_REGION,
                        thingSupervisorProps,
                        shardingSettings,
                        ShardRegionExtractor.of(numberOfShards, getContext().getSystem()));

        // start cluster singleton for namespace ops
        ClusterUtil.startSingleton(getContext(), CLUSTER_ROLE, ThingNamespaceOpsActor.ACTOR_NAME,
                ThingNamespaceOpsActor.props(pubSubMediator, config));

        retrieveStatisticsDetailsResponseSupplier = RetrieveStatisticsDetailsResponseSupplier.of(thingsShardRegion,
                ThingsMessagingConstants.SHARD_REGION, log);

        final HealthConfigReader healthConfig = configReader.health();
        final HealthCheckingActorOptions.Builder hcBuilder =
                HealthCheckingActorOptions.getBuilder(healthConfig.enabled(), healthConfig.getInterval());
        if (healthConfig.persistenceEnabled()) {
            hcBuilder.enablePersistenceCheck();
        }

        final HealthCheckingActorOptions healthCheckingActorOptions = hcBuilder.build();
        final ActorRef healthCheckingActor = startChildActor(DefaultHealthCheckingActorFactory.ACTOR_NAME,
                DefaultHealthCheckingActorFactory.props(healthCheckingActorOptions, MongoHealthChecker.props()));

        final int tagsStreamingCacheSize = config.getInt(ConfigKeys.THINGS_TAGS_STREAMING_CACHE_SIZE);
        final ActorRef persistenceStreamingActor = startChildActor(ThingsPersistenceStreamingActorCreator.ACTOR_NAME,
                ThingsPersistenceStreamingActorCreator.props(config, tagsStreamingCacheSize));

        pubSubMediator.tell(new DistributedPubSubMediator.Put(getSelf()), getSelf());
        pubSubMediator.tell(new DistributedPubSubMediator.Put(persistenceStreamingActor), getSelf());

        final HttpConfigReader httpConfig = configReader.http();
        String hostname = httpConfig.getHostname();
        if (hostname.isEmpty()) {
            hostname = ConfigUtil.getLocalHostAddress();
            log.info("No explicit hostname configured, using HTTP hostname <{}>.", hostname);
        }
        final CompletionStage<ServerBinding> binding = Http.get(getContext().system())
                .bindAndHandle(
                        createRoute(getContext().system(), healthCheckingActor).flow(getContext().system(),
                                materializer),
                        ConnectHttp.toHost(hostname, httpConfig.getPort()), materializer);

        binding.thenAccept(theBinding -> CoordinatedShutdown.get(getContext().getSystem()).addTask(
                CoordinatedShutdown.PhaseServiceUnbind(), "shutdown_health_http_endpoint", () -> {
                    log.info("Gracefully shutting down status/health HTTP endpoint..");
                    return theBinding.terminate(Duration.ofSeconds(1))
                            .handle((httpTerminated, e) -> Done.getInstance());
                })
        );
        binding.thenAccept(this::logServerBinding)
                .exceptionally(failure -> {
                    log.error(failure, "Something very bad happened: {}", failure.getMessage());
                    getContext().system().terminate();
                    return null;
                });
    }

    /**
     * Creates Akka configuration object Props for this ThingsRootActor.
     *
     * @param configReader the configuration reader of this service.
     * @param pubSubMediator the PubSub mediator Actor.
     * @param materializer the materializer for the akka actor system
     * @return the Akka configuration Props object.
     */
    static Props props(final ServiceConfigReader configReader,
            final ActorRef pubSubMediator,
            final ActorMaterializer materializer,
            final ThingSnapshotter.Create thingSnapshotterCreate) {

        return Props.create(ThingsRootActor.class, new Creator<ThingsRootActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ThingsRootActor create() {
                return new ThingsRootActor(configReader, pubSubMediator, materializer, thingSnapshotterCreate);
            }
        });
    }

    private static Route createRoute(final ActorSystem actorSystem, final ActorRef healthCheckingActor) {
        final StatusRoute statusRoute = new StatusRoute(new ClusterStatusSupplier(Cluster.get(actorSystem)),
                healthCheckingActor, actorSystem);

        return logRequest("http-request", () -> logResult("http-response", statusRoute::buildStatusRoute));
    }

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return strategy;
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(RetrieveStatisticsDetails.class, this::handleRetrieveStatisticsDetails)
                .match(Status.Failure.class, f -> log.error(f.cause(), "Got failure: {}", f))
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private void handleRetrieveStatisticsDetails(final RetrieveStatisticsDetails command) {
        log.info("Sending the namespace stats of the things shard as requested..");
        PatternsCS.pipe(retrieveStatisticsDetailsResponseSupplier
                .apply(command.getDittoHeaders()), getContext().dispatcher()).to(getSender());
    }

    private ActorRef startChildActor(final String actorName, final Props props) {
        log.info("Starting child actor <{}>.", actorName);
        return getContext().actorOf(props, actorName);
    }

    private void logServerBinding(final ServerBinding serverBinding) {
        log.info("Bound to address {}:{}", serverBinding.localAddress().getHostString(),
                serverBinding.localAddress().getPort());
    }

    private static Props getThingSupervisorActorProps(final Config config, final ActorRef pubSubMediator,
            final ThingSnapshotter.Create thingSnapshotterCreate) {

        final Duration minBackOff = config.getDuration(ConfigKeys.Thing.SUPERVISOR_EXPONENTIAL_BACKOFF_MIN);
        final Duration maxBackOff = config.getDuration(ConfigKeys.Thing.SUPERVISOR_EXPONENTIAL_BACKOFF_MAX);
        final double randomFactor = config.getDouble(ConfigKeys.Thing.SUPERVISOR_EXPONENTIAL_BACKOFF_RANDOM_FACTOR);

        return ThingSupervisorActor.props(pubSubMediator, minBackOff, maxBackOff, randomFactor,
                ThingPersistenceActorPropsFactory.getInstance(pubSubMediator, thingSnapshotterCreate));
    }

}

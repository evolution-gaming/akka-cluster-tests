package com.evolutiongaming.multinode.sharding.common

import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.cluster.sharding.ShardCoordinator.LeastShardAllocationStrategy
import com.codahale.metrics.MetricRegistry
import com.evolutiongaming.cluster.{AdaptiveAllocationStrategy, CountControl, ShardedMsg}
import com.evolutiongaming.multinode.common.AbstractMultiNodeSpec
import com.evolutiongaming.multinode.sharding.actor.ShardedActor

import scala.concurrent.duration._

abstract class ShardingMultiNodeSpec(val multiJvmConfig: ShardingMultiNodeConfig)
  extends AbstractMultiNodeSpec(multiJvmConfig) {

  def startSharding(sys: ActorSystem): ActorRef = {

    val settings = ClusterShardingSettings(sys)

    val fallbackStrategy = new LeastShardAllocationStrategy(
      rebalanceThreshold = settings.tuningParameters.leastShardAllocationRebalanceThreshold,
      maxSimultaneousRebalance = settings.tuningParameters.leastShardAllocationMaxSimultaneousRebalance)

    val strategy = AdaptiveAllocationStrategy(
      typeName = ShardedActor.TypeName,
      rebalanceThresholdPercent = 20,
      cleanupPeriod = 10.seconds,
      metricRegistry = new MetricRegistry,
      countControl = CountControl.Increment,
      fallbackStrategy = fallbackStrategy,
      maxSimultaneousRebalance = 10,
      nodesToDeallocate = () => Set.empty)(sys, sys.dispatcher)

    val extractShardId = strategy wrapExtractShardId multiJvmConfig.extractShardId

    ClusterSharding(sys).start(
      typeName = ShardedActor.TypeName,
      entityProps = Props[ShardedActor],
      settings = settings,
      extractEntityId = ShardedMsg.ExtractEntityId,
      extractShardId = extractShardId,
      allocationStrategy = strategy,
      handOffStopMessage = PoisonPill)
  }

  lazy val shardRegion: ActorRef = startSharding(system)

  protected trait ShardingMultiNodeScope extends MultiNodeScope {
    val NumberOfActors = 1000
  }
}
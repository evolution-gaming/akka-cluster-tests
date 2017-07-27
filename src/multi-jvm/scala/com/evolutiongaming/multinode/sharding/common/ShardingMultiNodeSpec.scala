package com.evolutiongaming.multinode.sharding.common

import akka.actor.{ActorRef, ActorSystem, Address, PoisonPill, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import com.codahale.metrics.MetricRegistry
import com.evolutiongaming.cluster.{AdaptiveAllocationStrategy, CountControl, ShardedMsg, SingleNodeAllocationStrategy}
import com.evolutiongaming.multinode.common.AbstractMultiNodeSpec
import com.evolutiongaming.multinode.sharding.actor.ShardedActor

import scala.collection.immutable
import scala.compat.Platform
import scala.concurrent.duration._

abstract class ShardingMultiNodeSpec(val multiJvmConfig: ShardingMultiNodeConfig)
  extends AbstractMultiNodeSpec(multiJvmConfig) {

  def metered(name: String)(f: => Unit): Unit = {
    val before = Platform.currentTime
    f
    val after = Platform.currentTime
    println(s"Execution time of $name for ${multiJvmConfig.shardType}: ${after - before}")
  }

  override def initialParticipants: Int = multiJvmConfig.ourRoles.size

  override def seedNodes: immutable.IndexedSeq[Address] = Vector(multiJvmConfig.first)

  @volatile
  var singleNodeAllocationAddress: Option[Address] = None

  def startSharding(sys: ActorSystem): ActorRef = {

    val MaxSimultaneousRebalance = 10000

    val settings = ClusterShardingSettings(sys)

    val fallbackStrategy = new SingleNodeAllocationStrategy(
      address = singleNodeAllocationAddress,
      maxSimultaneousRebalance = MaxSimultaneousRebalance,
      nodesToDeallocate = () => Set.empty[Address])(sys, sys.dispatcher)

    val strategy = AdaptiveAllocationStrategy(
      typeName = ShardedActor.TypeName,
      rebalanceThresholdPercent = 20,
      cleanupPeriod = 10.seconds,
      metricRegistry = new MetricRegistry,
      countControl = CountControl.Increment,
      fallbackStrategy = fallbackStrategy,
      maxSimultaneousRebalance = MaxSimultaneousRebalance,
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
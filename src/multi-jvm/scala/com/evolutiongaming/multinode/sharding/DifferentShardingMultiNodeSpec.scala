package com.evolutiongaming.multinode.sharding

import akka.actor.Address
import akka.cluster.Cluster
import com.evolutiongaming.multinode.common.AbstractMultiNodeSpec
import com.evolutiongaming.multinode.sharding.common.{ShardingIdentityMultiNodeConfig, ShardingMultiNodeConfig, ShardingUniformMultiNodeConfig}

import scala.collection.immutable
import scala.compat.Platform

class ShardingUniformRestartMultiJvmNode1 extends ShardingUniformRestartMultiNodeSpec
class ShardingUniformRestartMultiJvmNode2 extends ShardingUniformRestartMultiNodeSpec
class ShardingUniformRestartMultiJvmNode3 extends ShardingUniformRestartMultiNodeSpec
class ShardingUniformRestartMultiJvmNode4 extends ShardingUniformRestartMultiNodeSpec

abstract class ShardingUniformRestartMultiNodeSpec
  extends DifferentShardingMultiNodeSpec(ShardingUniformMultiNodeConfig)

class ShardingIdentityRestartMultiJvmNode1 extends ShardingIdentityRestartMultiNodeSpec
class ShardingIdentityRestartMultiJvmNode2 extends ShardingIdentityRestartMultiNodeSpec
class ShardingIdentityRestartMultiJvmNode3 extends ShardingIdentityRestartMultiNodeSpec
class ShardingIdentityRestartMultiJvmNode4 extends ShardingIdentityRestartMultiNodeSpec

abstract class ShardingIdentityRestartMultiNodeSpec
  extends DifferentShardingMultiNodeSpec(ShardingIdentityMultiNodeConfig)

abstract class DifferentShardingMultiNodeSpec(val multiJvmConfig: ShardingMultiNodeConfig)
  extends AbstractMultiNodeSpec(multiJvmConfig) {

  import multiJvmConfig._

  override def initialParticipants: Int = ourRoles.size

  override def seedNodes: immutable.IndexedSeq[Address] = Vector(first)

  def metered(name: String)(f: => Unit): Unit = {
    val before = Platform.currentTime
    f
    val after = Platform.currentTime
    println(s"Execution time of $name for ${multiJvmConfig.shardType}: ${after - before}")
  }

  "Cluster nodes" must {
    "be able to restart and join again" in new MultiNodeScope {

      within(DefaultMultiNodeTimeout) {

        cluster joinSeedNodes seedNodes
        awaitMembersUp(ourRoles.size)

        enterBarrier("initial-nodes-up")


        runOn(second) {
          metered("NODE RESTART") {
            shutdown(system, verifySystemShutdown = true)

            val newSystem = startNewSystem()
            Cluster(newSystem) joinSeedNodes seedNodes
            awaitMembersUp(ourRoles.size)
          }
          enterBarrier("node-up")
        }

        runExcluded(second) {
          awaitMembersUp(ourRoles.size - 1)
          awaitMembersUp(ourRoles.size)
          enterBarrier("node-up")
        }
      }
    }
  }
}
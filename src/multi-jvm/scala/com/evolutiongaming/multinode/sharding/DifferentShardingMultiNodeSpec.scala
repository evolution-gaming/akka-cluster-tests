package com.evolutiongaming.multinode.sharding

import akka.cluster.Cluster
import akka.cluster.sharding.ShardRegion
import akka.testkit.TestProbe
import com.evolutiongaming.cluster.ShardedMsg
import com.evolutiongaming.multinode.sharding.actor.ShardedActor
import com.evolutiongaming.multinode.sharding.common.{ShardingIdentityMultiNodeConfig, ShardingMultiNodeConfig, ShardingMultiNodeSpec, ShardingUniformMultiNodeConfig}

import scala.concurrent.duration._

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

abstract class DifferentShardingMultiNodeSpec(override val multiJvmConfig: ShardingMultiNodeConfig)
  extends ShardingMultiNodeSpec(multiJvmConfig) {

  import multiJvmConfig._

  "Cluster nodes" must {
    "be able to restart and join again" in new ShardingMultiNodeScope {

      within(DefaultMultiNodeTimeout) {

        cluster joinSeedNodes seedNodes
        awaitMembersUp(ourRoles.size)

        singleNodeAllocationAddress = clusterView.members map (_.uniqueAddress.address) find (_.port contains 9197)

        enterBarrier("initial-nodes-up")

        metered("SHARDING") {

          for (i <- 1 to NumberOfActors) {
            shardRegion ! ShardedMsg(i.toString, ShardedActor.Get)
            expectMsg(0)
          }

          enterBarrier("sharding-started")

          for (i <- 1 to NumberOfActors) {
            shardRegion ! ShardedMsg(i.toString, ShardedActor.Increment)
            expectMsgPF() {
              case _ =>
            }
          }

          enterBarrier("sharding-incremented")

          for (i <- 1 to NumberOfActors) {
            shardRegion ! ShardedMsg(i.toString, ShardedActor.Get)
            expectMsg(4)
          }

          enterBarrier("sharding-read")

          shardRegion ! ShardRegion.GetClusterShardingStats(30.seconds)

          expectMsgPF() {
            case msg => println(s"Sharding before the node restart: $msg")
          }

          enterBarrier("sharding-stats")

          runOn(second) {

            shutdown(system, verifySystemShutdown = true)

            val newSystem = startNewSystem()
            Cluster(newSystem) joinSeedNodes seedNodes

            awaitMembersUp(ourRoles.size, sys = newSystem)

            enterBarrier("node-up")

            val newProbe = TestProbe()(newSystem)

            val newRegion = startSharding(newSystem)

            for (i <- 1 to NumberOfActors) {
              newRegion.tell(ShardedMsg(i.toString, ShardedActor.Get), newProbe.ref)
              newProbe.expectMsgPF() {
                case msg =>
              }
            }

            enterBarrier("sharding-read-up")
          }

          runExcluded(second) {
            awaitMembersUp(ourRoles.size - 1)
            awaitMembersUp(ourRoles.size)
            enterBarrier("node-up")

            metered("SHARDING-READ-UP") {
              for (i <- 1 to NumberOfActors) {
                shardRegion ! ShardedMsg(i.toString, ShardedActor.Get)
                expectMsgPF() {
                  case _ =>
                }
              }
            }

            enterBarrier("sharding-read-up")

            shardRegion ! ShardRegion.GetClusterShardingStats(30.seconds)

            expectMsgPF() {
              case msg => println(s"Sharding after the node restart: $msg")
            }
          }
        }
      }
    }
  }
}
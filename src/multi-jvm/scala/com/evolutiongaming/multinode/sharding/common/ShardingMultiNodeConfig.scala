package com.evolutiongaming.multinode.sharding.common

import com.evolutiongaming.cluster.ExtractShardId
import com.evolutiongaming.multinode.common.CommonMultiNodeConfig

object ShardingUniformMultiNodeConfig extends ShardingMultiNodeConfig { def shardType = "uniform"}
object ShardingIdentityMultiNodeConfig extends ShardingMultiNodeConfig { def shardType = "identity"}

abstract class ShardingMultiNodeConfig extends CommonMultiNodeConfig {

  def shardType: String

  lazy val fourth = role("fourth")
  override def ourRoles = super.ourRoles :+ fourth

  val extractShardId = shardType match {
    case "uniform"  => ExtractShardId.uniform(numberOfShards = 10)
    case "identity" => ExtractShardId.identity
    case x          => sys error s"unexpected extract-shard-id: $x"
  }
}
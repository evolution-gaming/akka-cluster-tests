package com.evolutiongaming.multinode.sharding.common

import com.evolutiongaming.multinode.common.CommonMultiNodeConfig
import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.immutable

object ShardingUniformMultiNodeConfig extends ShardingMultiNodeConfig { def shardType = "uniform"}
object ShardingIdentityMultiNodeConfig extends ShardingMultiNodeConfig { def shardType = "identity"}

abstract class ShardingMultiNodeConfig extends CommonMultiNodeConfig {

  def shardType: String

  lazy val fourth = role("fourth")
  override def ourRoles = immutable.Seq(first, second, third, fourth)

  def shardingConfig: Config = ConfigFactory parseString
    s"""evolutiongaming.cluster.sharding.GameTable.extract-shard-id = "$shardType""""
}
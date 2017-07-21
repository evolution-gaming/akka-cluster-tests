package com.evolutiongaming.multinode.common

import akka.cluster.MultiNodeClusterSpec
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.immutable

trait CommonMultiNodeConfig extends MultiNodeConfig {

  testTransport(on = true)

  val first = role("first")
  val second = role("second")
  val third = role("third")
  def ourRoles = immutable.Seq(first, second, third)
  def roleName: String = ourRoles(MultiNodeSpec.selfIndex).name
  def configName: String = roleName

  def shardingConfig: Config

  commonConfig(debugConfig(on = true)
    withFallback shardingConfig
    withFallback MultiNodeClusterSpec.clusterConfig(failureDetectorPuppet = true))

  for (role <- ourRoles) nodeConfig(role)(ConfigFactory load s"${role.name}.conf")
}

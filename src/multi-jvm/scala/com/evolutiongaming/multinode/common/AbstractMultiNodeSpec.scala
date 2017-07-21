package com.evolutiongaming.multinode.common

import java.time.LocalTime

import akka.actor.{ActorSystem, Address}
import akka.cluster.MultiNodeClusterSpec
import akka.remote.testconductor.RoleName
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.collection.immutable
import scala.concurrent.duration._
import scala.util.Properties

abstract class AbstractMultiNodeSpec(multiJvmConfig: CommonMultiNodeConfig)
  extends MultiNodeSpec(
    multiJvmConfig,
    config => {
      ConfigFactory.invalidateCaches()
      Properties.setProp("config.resource", s"${multiJvmConfig.configName}.conf")
      val system = ActorSystem("clusterTest", config)
      system
    }) with MultiNodeClusterSpec with ImplicitSender with LazyLogging {

  import multiJvmConfig._

  def seedNodes: immutable.IndexedSeq[Address] = Vector(first)

  override def enterBarrier(name: String*): Unit = {
    logger info  s"Entering barrier: ${name.mkString}"
    println(s"${LocalTime.now()} Entering barrier: ${name.mkString}")
    super.enterBarrier(name: _*)
    println(s"${LocalTime.now()} ENTERED BARRIER: ${name.mkString}")
  }

  def runExcluded(nodes: RoleName*)(f: => Unit): Unit = if (!isNode(nodes: _*)) f

  protected trait MultiNodeScope {
    val DefaultMultiNodeTimeout = 5.minutes
    val selfIndex = ourRoles indexOf myself
  }
}

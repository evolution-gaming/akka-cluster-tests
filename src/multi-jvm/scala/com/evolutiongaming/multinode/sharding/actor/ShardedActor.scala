package com.evolutiongaming.multinode.sharding.actor

import akka.actor.{Actor, ReceiveTimeout}

import scala.concurrent.duration._

class ShardedActor extends Actor {

  import akka.cluster.sharding.ShardRegion.Passivate

  import ShardedActor._

  context.setReceiveTimeout(120.seconds)

  var count = 0

  def receive: Receive = {
    case Increment      =>
      count += 1
      sender() ! count
    case Get            => sender() ! count
    case ReceiveTimeout => context.parent ! Passivate(stopMessage = Stop)
    case Stop           => context.stop(self)
  }
}

object ShardedActor {

  val TypeName = "ShardedActor"

  case object Increment
  case object Get
  case object Stop
}
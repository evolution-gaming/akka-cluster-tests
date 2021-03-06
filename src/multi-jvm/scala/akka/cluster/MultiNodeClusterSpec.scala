/**
  * Copyright (C) 2009-2017 Lightbend Inc. <http://www.lightbend.com>
  */
package akka.cluster

import java.util.UUID

import language.implicitConversions
import org.scalatest._
import org.scalatest.exceptions.TestCanceledException
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{FlightRecordingSupport, MultiNodeSpec, MultiNodeSpecCallbacks}
import akka.testkit._
import akka.testkit.TestEvent._
import akka.actor.{ActorSystem, Address}
import akka.event.Logging.ErrorLevel

import scala.concurrent.duration._
import scala.collection.immutable
import java.util.concurrent.ConcurrentHashMap

import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.RootActorPath

trait ScalaTestMultiNodeSpec extends MultiNodeSpecCallbacks
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def beforeAll() = multiNodeSpecBeforeAll()

  override def afterAll() = multiNodeSpecAfterAll()
}

object MultiNodeClusterSpec {

  def clusterConfigWithFailureDetectorPuppet: Config =
    ConfigFactory.parseString("akka.cluster.failure-detector.implementation-class = akka.cluster.FailureDetectorPuppet").
      withFallback(clusterConfig)

  def clusterConfig(failureDetectorPuppet: Boolean): Config =
    if (failureDetectorPuppet) clusterConfigWithFailureDetectorPuppet else clusterConfig

  def clusterConfig: Config = ConfigFactory.parseString(s"""
    akka.actor.provider = cluster
    akka.actor.warn-about-java-serializer-usage = off
    akka.cluster {
      auto-down-unreachable-after         = off
      allow-weakly-up-members             = off
      jmx.enabled                         = off
      gossip-interval                     = 200ms
      leader-actions-interval             = 200ms
      unreachable-nodes-reaper-interval   = 500ms
      periodic-tasks-initial-delay        = 300ms
      publish-stats-interval              = 0s # always, when it happens
      failure-detector.heartbeat-interval = 500ms
    }
    akka.loglevel = INFO
    akka.log-dead-letters = off
    akka.log-dead-letters-during-shutdown = off
    akka.remote {
      log-remote-lifecycle-events = off
      artery.advanced.flight-recorder {
        enabled=on
        destination=target/flight-recorder-${UUID.randomUUID().toString}.afr
      }
    }
    akka.loggers = ["akka.testkit.TestEventListener"]

    """)

  // sometimes we need to coordinate test shutdown with messages instead of barriers
  object EndActor {
    case object SendEnd
    case object End
    case object EndAck
  }

  class EndActor(testActor: ActorRef, target: Option[Address]) extends Actor {
    import EndActor._
    def receive = {
      case SendEnd =>
        target foreach { t =>
          context.actorSelection(RootActorPath(t) / self.path.elements) ! End
        }
      case End =>
        testActor forward End
        sender() ! EndAck
      case EndAck =>
        testActor forward EndAck
    }
  }
}

trait MultiNodeClusterSpec extends Suite with ScalaTestMultiNodeSpec with FlightRecordingSupport { self: MultiNodeSpec =>

  override def initialParticipants = roles.size

  private val cachedAddresses = new ConcurrentHashMap[RoleName, Address]

  override protected def atStartup(): Unit = {
    muteLog()
    self.atStartup()
  }

  override protected def afterTermination(): Unit = {
    self.afterTermination()
    if (failed || sys.props.get("akka.remote.artery.always-dump-flight-recorder").isDefined) {
      printFlightRecording()
    }
    deleteFlightRecorderFile()
  }

  def muteLog(sys: ActorSystem = system): Unit = {
    if (!sys.log.isDebugEnabled) {
      Seq(
        ".*Cluster Node.* - registered cluster JMX MBean.*",
        ".*Cluster Node.* - is starting up.*",
        ".*Shutting down cluster Node.*",
        ".*Cluster node successfully shut down.*",
        ".*Using a dedicated scheduler for cluster.*") foreach { s =>
        sys.eventStream.publish(Mute(EventFilter.info(pattern = s)))
      }

      muteDeadLetters(
        classOf[ClusterHeartbeatSender.Heartbeat],
        classOf[ClusterHeartbeatSender.HeartbeatRsp],
        classOf[GossipEnvelope],
        classOf[GossipStatus],
        classOf[InternalClusterAction.Tick],
        classOf[akka.actor.PoisonPill],
        classOf[akka.dispatch.sysmsg.DeathWatchNotification],
        classOf[akka.remote.transport.AssociationHandle.Disassociated],
        classOf[akka.remote.transport.ActorTransportAdapter.DisassociateUnderlying],
        classOf[akka.remote.transport.AssociationHandle.InboundPayload])(sys)

    }
  }

  def muteMarkingAsUnreachable(sys: ActorSystem = system): Unit =
    if (!sys.log.isDebugEnabled)
      sys.eventStream.publish(Mute(EventFilter.error(pattern = ".*Marking.* as UNREACHABLE.*")))

  def muteMarkingAsReachable(sys: ActorSystem = system): Unit =
    if (!sys.log.isDebugEnabled)
      sys.eventStream.publish(Mute(EventFilter.info(pattern = ".*Marking.* as REACHABLE.*")))

  override def afterAll(): Unit = {
    if (!log.isDebugEnabled) {
      muteDeadLetters()()
      system.eventStream.setLogLevel(ErrorLevel)
    }
    super.afterAll()
  }

  /**
    * Lookup the Address for the role.
    *
    * Implicit conversion from RoleName to Address.
    *
    * It is cached, which has the implication that stopping
    * and then restarting a role (jvm) with another address is not
    * supported.
    */
  implicit def address(role: RoleName): Address = {
    cachedAddresses.get(role) match {
      case null =>
        val address = node(role).address
        cachedAddresses.put(role, address)
        address
      case address => address
    }
  }

  // Cluster tests are written so that if previous step (test method) failed
  // it will most likely not be possible to run next step. This ensures
  // fail fast of steps after the first failure.
  private var failed = false
  override protected def withFixture(test: NoArgTest): Outcome =
    if (failed) {
      Canceled(new TestCanceledException("Previous step failed", 0))
    } else {
      val out = super.withFixture(test)
      if (!out.isSucceeded)
        failed = true
      out
    }

  def clusterView: ClusterReadView = cluster.readView

  def clusterView(sys: ActorSystem): ClusterReadView = Cluster(sys).readView

  /**
    * Get the cluster node to use.
    */
  def cluster: Cluster = Cluster(system)

  /**
    * Use this method for the initial startup of the cluster node.
    */
  def startClusterNode(): Unit = {
    if (clusterView.members.isEmpty) {
      cluster join myself
      awaitAssert(clusterView.members.map(_.address) should contain(address(myself)))
    } else
      clusterView.self
  }

  /**
    * Initialize the cluster of the specified member
    * nodes (roles) and wait until all joined and `Up`.
    * First node will be started first  and others will join
    * the first.
    */
  def awaitClusterUp(roles: RoleName*): Unit = {
    runOn(roles.head) {
      // make sure that the node-to-join is started before other join
      startClusterNode()
    }
    enterBarrier(roles.head.name + "-started")
    if (roles.tail.contains(myself)) {
      cluster.join(roles.head)
    }
    if (roles.contains(myself)) {
      awaitMembersUp(numberOfMembers = roles.length)
    }
    enterBarrier(roles.map(_.name).mkString("-") + "-joined")
  }

  /**
    * Join the specific node within the given period by sending repeated join
    * requests at periodic intervals until we succeed.
    */
  def joinWithin(joinNode: RoleName, max: Duration = remainingOrDefault, interval: Duration = 1.second): Unit = {
    def memberInState(member: Address, status: Seq[MemberStatus]): Boolean =
      clusterView.members.exists { m => (m.address == member) && status.contains(m.status) }

    cluster join joinNode
    awaitCond({
      clusterView.refreshCurrentState()
      if (memberInState(joinNode, List(MemberStatus.up)) &&
        memberInState(myself, List(MemberStatus.Joining, MemberStatus.Up)))
        true
      else {
        cluster join joinNode
        false
      }
    }, max, interval)
  }

  /**
    * Assert that the member addresses match the expected addresses in the
    * sort order used by the cluster.
    */
  def assertMembers(gotMembers: Iterable[Member], expectedAddresses: Address*): Unit = {
    import Member.addressOrdering
    val members = gotMembers.toIndexedSeq
    members.size should ===(expectedAddresses.length)
    expectedAddresses.sorted.zipWithIndex.foreach { case (a, i) => members(i).address should ===(a) }
  }

  /**
    * Note that this can only be used for a cluster with all members
    * in Up status, i.e. use `awaitMembersUp` before using this method.
    * The reason for that is that the cluster leader is preferably a
    * member with status Up or Leaving and that information can't
    * be determined from the `RoleName`.
    */
  def assertLeader(nodesInCluster: RoleName*): Unit =
    if (nodesInCluster.contains(myself)) assertLeaderIn(nodesInCluster.to[immutable.Seq])

  /**
    * Assert that the cluster has elected the correct leader
    * out of all nodes in the cluster. First
    * member in the cluster ring is expected leader.
    *
    * Note that this can only be used for a cluster with all members
    * in Up status, i.e. use `awaitMembersUp` before using this method.
    * The reason for that is that the cluster leader is preferably a
    * member with status Up or Leaving and that information can't
    * be determined from the `RoleName`.
    */
  def assertLeaderIn(nodesInCluster: immutable.Seq[RoleName]): Unit =
    if (nodesInCluster.contains(myself)) {
      nodesInCluster.length should not be (0)
      val expectedLeader = roleOfLeader(nodesInCluster)
      val leader = clusterView.leader
      val isLeader = leader == Some(clusterView.selfAddress)
      assert(
        isLeader == isNode(expectedLeader),
        "expectedLeader [%s], got leader [%s], members [%s]".format(expectedLeader, leader, clusterView.members))
      clusterView.status should (be(MemberStatus.Up) or be(MemberStatus.Leaving))
    }

  /**
    * Wait until the expected number of members has status Up has been reached.
    * Also asserts that nodes in the 'canNotBePartOfMemberRing' are *not* part of the cluster ring.
    */
  def awaitMembersUp(
    numberOfMembers:          Int,
    canNotBePartOfMemberRing: Set[Address]   = Set.empty,
    timeout:                  FiniteDuration = 30.seconds,
    sys: ActorSystem = system): Unit = {
    within(timeout) {
      if (canNotBePartOfMemberRing.nonEmpty) // don't run this on an empty set
        awaitAssert(canNotBePartOfMemberRing foreach (a => clusterView(sys).members.map(_.address) should not contain a))
      awaitAssert(clusterView(sys).members.size should ===(numberOfMembers))
      awaitAssert(clusterView(sys).members.map(_.status) should ===(Set(MemberStatus.Up)))
      // clusterView(sys).leader is updated by LeaderChanged, await that to be updated also
      val expectedLeader = clusterView(sys).members.headOption.map(_.address)
      awaitAssert(clusterView(sys).leader should ===(expectedLeader))
    }
  }

  def awaitAllReachable(): Unit =
    awaitAssert(clusterView.unreachableMembers should ===(Set.empty))

  /**
    * Wait until the specified nodes have seen the same gossip overview.
    */
  def awaitSeenSameState(addresses: Address*): Unit =
    awaitAssert((addresses.toSet diff clusterView.seenBy) should ===(Set.empty))

  /**
    * Leader according to the address ordering of the roles.
    * Note that this can only be used for a cluster with all members
    * in Up status, i.e. use `awaitMembersUp` before using this method.
    * The reason for that is that the cluster leader is preferably a
    * member with status Up or Leaving and that information can't
    * be determined from the `RoleName`.
    */
  def roleOfLeader(nodesInCluster: immutable.Seq[RoleName] = roles): RoleName = {
    nodesInCluster.length should not be 0
    nodesInCluster.min
  }

  /**
    * Sort the roles in the address order used by the cluster node ring.
    */
  implicit val clusterOrdering: Ordering[RoleName] = new Ordering[RoleName] {
    import Member.addressOrdering
    def compare(x: RoleName, y: RoleName) = addressOrdering.compare(address(x), address(y))
  }

  def roleName(addr: Address): Option[RoleName] = roles.find(address(_) == addr)
}

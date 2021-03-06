# Cluster Client

An actor system that is not part of the cluster can communicate with actors
somewhere in the cluster via this @scala[@scaladoc[`ClusterClient`](akka.cluster.client.ClusterClient)]@java[@javadoc[`ClusterClient`](akka.cluster.client.ClusterClient)]. The client can of course be part of
another cluster. It only needs to know the location of one (or more) nodes to use as initial
contact points. It will establish a connection to a @scala[@scaladoc[`ClusterReceptionist`](akka.cluster.client.ClusterReceptionist)]@java[@javadoc[`ClusterReceptionist`](akka.cluster.client.ClusterReceptionist)] somewhere in
the cluster. It will monitor the connection to the receptionist and establish a new
connection if the link goes down. When looking for a new receptionist it uses fresh
contact points retrieved from previous establishment, or periodically refreshed contacts,
i.e. not necessarily the initial contact points.

@@@ note

@scala[@scaladoc[`ClusterClient`](akka.cluster.client.ClusterClient)]@java[@javadoc[`ClusterClient`](akka.cluster.client.ClusterClient)] should not be used when sending messages to actors that run
within the same cluster. Similar functionality as the @scala[@scaladoc[`ClusterClient`](akka.cluster.client.ClusterClient)]@java[@javadoc[`ClusterClient`](akka.cluster.client.ClusterClient)] is
provided in a more efficient way by @ref:[Distributed Publish Subscribe in Cluster](distributed-pub-sub.md) for actors that
belong to the same cluster.

@@@

Also, note it's necessary to change `akka.actor.provider` from `local`
to `remote` or `cluster` when using
the cluster client.

The receptionist is supposed to be started on all nodes, or all nodes with specified role,
in the cluster. The receptionist can be started with the @scala[@scaladoc[`ClusterClientReceptionist`](akka.cluster.client.ClusterClientReceptionist)]@java[@javadoc[`ClusterClientReceptionist`](akka.cluster.client.ClusterClientReceptionist)] extension
or as an ordinary actor.

You can send messages via the @scala[@scaladoc[`ClusterClient`](akka.cluster.client.ClusterClient)]@java[@javadoc[`ClusterClient`](akka.cluster.client.ClusterClient)] to any actor in the cluster that is registered
in the @scala[@scaladoc[`DistributedPubSubMediator`](akka.cluster.pubsub.DistributedPubSubMediator)]@java[@javadoc[`DistributedPubSubMediator`](akka.cluster.pubsub.DistributedPubSubMediator)] used by the @scala[@scaladoc[`ClusterReceptionist`](akka.cluster.client.ClusterReceptionist)]@java[@javadoc[`ClusterReceptionist`](akka.cluster.client.ClusterReceptionist)].
The @scala[@scaladoc[`ClusterClientReceptionist`](akka.cluster.client.ClusterClientReceptionist)]@java[@javadoc[`ClusterClientReceptionist`](akka.cluster.client.ClusterClientReceptionist)] provides methods for registration of actors that
should be reachable from the client. Messages are wrapped in `ClusterClient.Send`,
@scala[@scaladoc[`ClusterClient.SendToAll`](akka.cluster.client.ClusterClient$)]@java[`ClusterClient.SendToAll`] or @scala[@scaladoc[`ClusterClient.Publish`](akka.cluster.client.ClusterClient$)]@java[`ClusterClient.Publish`].

Both the @scala[@scaladoc[`ClusterClient`](akka.cluster.client.ClusterClient)]@java[@javadoc[`ClusterClient`](akka.cluster.client.ClusterClient)] and the @scala[@scaladoc[`ClusterClientReceptionist`](akka.cluster.client.ClusterClient)]@java[@javadoc[`ClusterClientReceptionist`](akka.cluster.client.ClusterClient)] emit events that can be subscribed to.
The @scala[@scaladoc[`ClusterClient`](akka.cluster.client.ClusterClient)]@java[@javadoc[`ClusterClient`](akka.cluster.client.ClusterClient)] sends out notifications in relation to having received a list of contact points
from the @scala[@scaladoc[`ClusterClientReceptionist`](akka.cluster.client.ClusterClientReceptionist)]@java[@javadoc[`ClusterClientReceptionist`](akka.cluster.client.ClusterClientReceptionist)]. One use of this list might be for the client to record its
contact points. A client that is restarted could then use this information to supersede any previously
configured contact points.

The @scala[@scaladoc[`ClusterClientReceptionist`](akka.cluster.client.ClusterClientReceptionist)]@java[@javadoc[`ClusterClientReceptionist`](akka.cluster.client.ClusterClientReceptionist)] sends out notifications in relation to having received a contact
from a @scala[@scaladoc[`ClusterClient`](akka.cluster.client.ClusterClient)]@java[@javadoc[`ClusterClient`](akka.cluster.client.ClusterClient)]. This notification enables the server containing the receptionist to become aware of
what clients are connected.

1. **ClusterClient.Send**

    The message will be delivered to one recipient with a matching path, if any such
    exists. If several entries match the path the message will be delivered
    to one random destination. The sender of the message can specify that local
    affinity is preferred, i.e. the message is sent to an actor in the same local actor
    system as the used receptionist actor, if any such exists, otherwise random to any other
    matching entry.

2. **ClusterClient.SendToAll**

    The message will be delivered to all recipients with a matching path.

3. **ClusterClient.Publish**

    The message will be delivered to all recipients Actors that have been registered as subscribers
    to the named topic.

Response messages from the destination actor are tunneled via the receptionist
to avoid inbound connections from other cluster nodes to the client:

* @scala[@scaladoc[`sender()`](akka.actor.Actor)]@java[@javadoc[`getSender()`](akka.actor.Actor)], as seen by the destination actor, is not the client itself,
  but the receptionist
* @scala[@scaladoc[`sender()`](akka.actor.Actor)] @java[@javadoc[`getSender()`](akka.actor.Actor)] of the response messages, sent back from the destination and seen by the client,
  is `deadLetters`
  
since the client should normally send subsequent messages via the @scala[@scaladoc[`ClusterClient`](akka.cluster.client.ClusterClient)]@java[@javadoc[`ClusterClient`](akka.cluster.client.ClusterClient)].
It is possible to pass the original sender inside the reply messages if
the client is supposed to communicate directly to the actor in the cluster.

While establishing a connection to a receptionist the @scala[@scaladoc[`ClusterClient`](akka.cluster.client.ClusterClient)]@java[@javadoc[`ClusterClient`](akka.cluster.client.ClusterClient)] will buffer
messages and send them when the connection is established. If the buffer is full
the @scala[@scaladoc[`ClusterClient`](akka.cluster.client.ClusterClient)]@java[@javadoc[`ClusterClient`](akka.cluster.client.ClusterClient)] will drop old messages when new messages are sent via the client.
The size of the buffer is configurable and it can be disabled by using a buffer size of 0.

It's worth noting that messages can always be lost because of the distributed nature
of these actors. As always, additional logic should be implemented in the destination
(acknowledgement) and in the client (retry) actors to ensure at-least-once message delivery.

## An Example

On the cluster nodes first start the receptionist. Note, it is recommended to load the extension
when the actor system is started by defining it in the `akka.extensions` configuration property:

```
akka.extensions = ["akka.cluster.client.ClusterClientReceptionist"]
```

Next, register the actors that should be available for the client.

Scala
:  @@snip [ClusterClientSpec.scala]($akka$/akka-cluster-tools/src/multi-jvm/scala/akka/cluster/client/ClusterClientSpec.scala) { #server }

Java
:  @@snip [ClusterClientTest.java]($akka$/akka-cluster-tools/src/test/java/akka/cluster/client/ClusterClientTest.java) { #server }

On the client you create the @scala[@scaladoc[`ClusterClient`](akka.cluster.client.ClusterClient)]@java[@javadoc[`ClusterClient`](akka.cluster.client.ClusterClient)] actor and use it as a gateway for sending
messages to the actors identified by their path (without address information) somewhere
in the cluster.

Scala
:  @@snip [ClusterClientSpec.scala]($akka$/akka-cluster-tools/src/multi-jvm/scala/akka/cluster/client/ClusterClientSpec.scala) { #client }

Java
:  @@snip [ClusterClientTest.java]($akka$/akka-cluster-tools/src/test/java/akka/cluster/client/ClusterClientTest.java) { #client }

The `initialContacts` parameter is a @scala[`Set[ActorPath]`]@java[`Set<ActorPath>`], which can be created like this:

Scala
:  @@snip [ClusterClientSpec.scala]($akka$/akka-cluster-tools/src/multi-jvm/scala/akka/cluster/client/ClusterClientSpec.scala) { #initialContacts }

Java
:  @@snip [ClusterClientTest.java]($akka$/akka-cluster-tools/src/test/java/akka/cluster/client/ClusterClientTest.java) { #initialContacts }

You will probably define the address information of the initial contact points in configuration or system property.
See also [Configuration](#cluster-client-config).

A more comprehensive sample is available in the tutorial named
@scala[[Distributed workers with Akka and Scala](https://github.com/typesafehub/activator-akka-distributed-workers).]
@java[[Distributed workers with Akka and Java](https://github.com/typesafehub/activator-akka-distributed-workers-java).]

## ClusterClientReceptionist Extension

In the example above the receptionist is started and accessed with the `akka.cluster.client.ClusterClientReceptionist` extension.
That is convenient and perfectly fine in most cases, but it can be good to know that it is possible to
start the `akka.cluster.client.ClusterReceptionist` actor as an ordinary actor and you can have several
different receptionists at the same time, serving different types of clients.

Note that the @scala[@scaladoc[`ClusterClientReceptionist`](akka.cluster.client.ClusterClientReceptionist)]@java[@javadoc[`ClusterClientReceptionist`](akka.cluster.client.ClusterClientReceptionist)] uses the @scala[@scaladoc[`DistributedPubSub`](akka.cluster.pubsub.DistributedPubSub)]@java[@javadoc[`DistributedPubSub`](akka.cluster.pubsub.DistributedPubSub)] extension, which is described
in @ref:[Distributed Publish Subscribe in Cluster](distributed-pub-sub.md).

It is recommended to load the extension when the actor system is started by defining it in the
`akka.extensions` configuration property:

```
akka.extensions = ["akka.cluster.client.ClusterClientReceptionist"]
```

## Events

As mentioned earlier, both the @scala[@scaladoc[`ClusterClient`](akka.cluster.client.ClusterClient)]@java[@javadoc[`ClusterClient`](akka.cluster.client.ClusterClient)] and @scala[@scaladoc[`ClusterClientReceptionist`](akka.cluster.client.ClusterClientReceptionist)]@java[@javadoc[`ClusterClientReceptionist`](akka.cluster.client.ClusterClientReceptionist)] emit events that can be subscribed to.
The following code snippet declares an actor that will receive notifications on contact points (addresses to the available
receptionists), as they become available. The code illustrates subscribing to the events and receiving the @scala[@scaladoc[`ClusterClient`](akka.cluster.client.ClusterClient)]@java[@javadoc[`ClusterClient`](akka.cluster.client.ClusterClient)]
initial state.

Scala
:  @@snip [ClusterClientSpec.scala]($akka$/akka-cluster-tools/src/multi-jvm/scala/akka/cluster/client/ClusterClientSpec.scala) { #clientEventsListener }

Java
:  @@snip [ClusterClientTest.java]($akka$/akka-cluster-tools/src/test/java/akka/cluster/client/ClusterClientTest.java) { #clientEventsListener }

Similarly we can have an actor that behaves in a similar fashion for learning what cluster clients are connected to a @scala[@scaladoc[`ClusterClientReceptionist`](akka.cluster.client.ClusterClientReceptionist)]@java[@javadoc[`ClusterClientReceptionist`](akka.cluster.client.ClusterClientReceptionist)]:

Scala
:  @@snip [ClusterClientSpec.scala]($akka$/akka-cluster-tools/src/multi-jvm/scala/akka/cluster/client/ClusterClientSpec.scala) { #receptionistEventsListener }

Java
:  @@snip [ClusterClientTest.java]($akka$/akka-cluster-tools/src/test/java/akka/cluster/client/ClusterClientTest.java) { #receptionistEventsListener }

## Dependencies

To use the Cluster Client you must add the following dependency in your project.

sbt
:   @@@vars
    ```
    "com.typesafe.akka" %% "akka-cluster-tools" % "$akka.version$"
    ```
    @@@

Maven
:   @@@vars
    ```
    <dependency>
      <groupId>com.typesafe.akka</groupId>
      <artifactId>akka-cluster-tools_$scala.binary_version$</artifactId>
      <version>$akka.version$</version>
    </dependency>
    ```
    @@@

<a id="cluster-client-config"></a>
## Configuration

The @scala[@scaladoc[`ClusterClientReceptionist`](akka.cluster.client.ClusterClientReceptionist)]@java[@javadoc[`ClusterClientReceptionist`](akka.cluster.client.ClusterClientReceptionist)] extension (or @scala[@scaladoc[`ClusterReceptionistSettings`](akka.cluster.client.ClusterReceptionistSettings)]@java[@javadoc[`ClusterReceptionistSettings`](akka.cluster.client.ClusterReceptionistSettings)]) can be configured
with the following properties:

@@snip [reference.conf]($akka$/akka-cluster-tools/src/main/resources/reference.conf) { #receptionist-ext-config }

The following configuration properties are read by the @scala[@scaladoc[`ClusterClientSettings`](akka.cluster.client.ClusterClientSettings)]@java[@javadoc[`ClusterClientSettings`](akka.cluster.client.ClusterClientSettings)]
when created with a @scala[@scaladoc[`ActorSystem`](akka.actor.ActorSystem)]@java[@javadoc[`ActorSystem`](akka.actor.ActorSystem)] parameter. It is also possible to amend the @scala[@scaladoc[`ClusterClientSettings`](akka.cluster.client.ClusterClientSettings)]@java[@javadoc[`ClusterClientSettings`](akka.cluster.client.ClusterClientSettings)]
or create it from another config section with the same layout as below. @scala[@scaladoc[`ClusterClientSettings`](akka.cluster.client.ClusterClientSettings)]@java[@javadoc[`ClusterClientSettings`](akka.cluster.client.ClusterClientSettings)] is
a parameter to the @scala[@scaladoc[`ClusterClient.props`](akka.cluster.client.ClusterClient$)]@java[@javadoc[`ClusterClient.props`](akka.cluster.client.ClusterClient$)] factory method, i.e. each client can be configured
with different settings if needed.

@@snip [reference.conf]($akka$/akka-cluster-tools/src/main/resources/reference.conf) { #cluster-client-config }

## Failure handling

When the cluster client is started it must be provided with a list of initial contacts which are cluster
nodes where receptionists are running. It will then repeatedly (with an interval configurable
by `establishing-get-contacts-interval`) try to contact those until it gets in contact with one of them.
While running, the list of contacts are continuously updated with data from the receptionists (again, with an
interval configurable with `refresh-contacts-interval`), so that if there are more receptionists in the cluster
than the initial contacts provided to the client the client will learn about them.

While the client is running it will detect failures in its connection to the receptionist by heartbeats
if more than a configurable amount of heartbeats are missed the client will try to reconnect to its known
set of contacts to find a receptionist it can access.

## When the cluster cannot be reached at all

It is possible to make the cluster client stop entirely if it cannot find a receptionist it can talk to
within a configurable interval. This is configured with the `reconnect-timeout`, which defaults to `off`.
This can be useful when initial contacts are provided from some kind of service registry, cluster node addresses
are entirely dynamic and the entire cluster might shut down or crash, be restarted on new addresses. Since the
client will be stopped in that case a monitoring actor can watch it and upon `Terminate` a new set of initial
contacts can be fetched and a new cluster client started.

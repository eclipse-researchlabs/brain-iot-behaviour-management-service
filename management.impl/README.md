# Behaviour Management Service

The Behaviour Management Service is an Event Consumer of last resort. It receives BRAIN-IoT events that have no registered Consumers.

Consumers of last resort are *only invoked locally*, that is on the same node that sent the event, so the Behaviour Management Service needs to run on all nodes.

On receiving an unhandled event, the Behaviour Management Service attempts to automatically deploy the required behaviour on the most appropriate node. If the required behaviour cannot be located in the BRIAN-IoT repository, or if after installation the event is still unhandled (e.g. because the installed service is not configured), then an error event is sent.

The Behaviour Management Service blacklists events it has already  handled to avoid repeatedly attempting to handle the same event.

## Meta-data

Smart behaviours and BRAIN-IoT events do not contain any explicit meta-data. Smart behaviours contain implicit meta-data in the form of OSGi Requirements & Capabilities, but these do not currently express *soft* requirements, such as preference to run on a server node or edge node.

However, explicit meta-data would be useful for the following use-cases:

1. If an optional acknowledgment event, for example the bundler-installer response event, does not have any consumers. The Behaviour Management Service needs to know that it's OK not to have any consumers, otherwise it will needlessly try to install a behaviour to consume these events.

2. A smart behaviour may need to be configured before it can be used. For example, a light controller may need to be configured with the identities of the lights it controls. The Behaviour Management Service can automatically install the light controller, but it cannot automatically configure it.

3. A smart behaviour may have a preference as to where it's installed. For example, it may
   be more appropriate to host an image processing/recognition algorithm on a server node, rather than installing locally on the node that sent the event. However, the Behaviour Management Service has no way to know this apart from the behaviour's OSGi requirements such as the Java execution environment:

   ```
   (&(osgi.ee=JavaSE)(version=1.8))
   ```

## Implementation

The Behaviour Management Service can mitigate the lack of explicit meta-data:

1. (Event where consumers are optional) Process as usual, event will be blacklisted after first attempt to process it.
2. (Smart behaviour that requires configuration before it can be used) This can be inferred if the event is still unhandled after the smart behaviour has been installed and an operator alert raised.
3. (Installation  node preference) This can't be really achieved without explicit meta-data. However, the behaviour management services on all nodes can be given the opportunity to **bid** on whether thay are *able* & *willing* to host the new behaviour. This allows local configuration of the behaviour management service on some nodes to accept or reject the installation of certain smart behaviours.

The Behaviour Management Service processes last-resort events as follows:

1. If the event type is on the blacklist, then ignore it.

2. Mark event type as *processing* and keep any subsequent events of this type to replay later.

3. If the requirement for a consumer of this event type can not be found in the BRAIN-IoT repository, then send an error event and add this event type to the blacklist.

4. Send a Management-Bid event to all nodes (including this node)

5. Respond to a Management-Bid event indicating node's ability and willingness to host behaviour as follows:

   If requirement can't be resolved (e.g. wrong architecture), then respond NoBid

   Otherwise respond with a bid integer where 0 is neutral, positive numbers indicate willingness to host and negative numbers indicate unwillingness to host.

6. Collect bid responses

   If no responses, send error event and add event type to blacklist

   Otherwise ask the highest bidder to install the behaviour. If all bids are equal, install locally, unless we NoBid in which case choose the first response.

7. Wait for highest bidder to complete install

   Add event type to blacklist.

   If install failed, send error event.

   Otherwise resend original event and any other events kept in step 2. Note: if these events are not consumed by the installed behaviour, we will ignore them as they are now blacklisted.






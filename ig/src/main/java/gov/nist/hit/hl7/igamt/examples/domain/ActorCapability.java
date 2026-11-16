package gov.nist.hit.hl7.igamt.examples.domain;

/**
 * What an actor is allowed to do on a step. A single step still records the actual from and to.
 */
public enum ActorCapability {
    SENDER,
    RECEIVER,
    BOTH
}

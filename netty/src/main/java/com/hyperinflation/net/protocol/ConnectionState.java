package com.hyperinflation.net.protocol;

/**
 * Connection lifecycle states, similar to Minecraft's protocol states.
 */
public enum ConnectionState {
    /** Initial connection. Client must send C2SHandshake. */
    HANDSHAKE,
    /** Fully connected. Receives all world data. */
    PLAY,
    /** Read-only observer. Receives world data but cannot send actions. */
    SPECTATE,
    /** Disconnecting. */
    CLOSING
}
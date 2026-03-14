import 'dart:async';
import 'dart:convert';
import 'package:web_socket_channel/web_socket_channel.dart';

// =============================================================================
//  PACKET IDs — must match Java PacketRegistry
// =============================================================================

class S2C {
  static const handshakeAck  = 0x01;
  static const worldSnapshot = 0x02;
  static const cityUpdate    = 0x03;
  static const agentUpdate   = 0x04;
  static const economyTick   = 0x05;
  static const eventLog      = 0x06;
  static const phaseChange   = 0x07;
  static const roundResult   = 0x08;
  static const keepAlive     = 0x09;
}

class C2S {
  static const handshake   = 0x01;
  static const keepAlive   = 0x02;
  static const moduleSwitch = 0x03;
  static const playerAction = 0x04;
  static const subscribe   = 0x05;
}

// =============================================================================
//  DECODED PACKET
// =============================================================================

class ServerPacket {
  final int pid;
  final Map<String, dynamic> data;
  ServerPacket(this.pid, this.data);
}

// =============================================================================
//  WORLD SERVICE — Packet-based WebSocket client
// =============================================================================

class WorldService {
  static const String _wsUrl = 'ws://localhost:8080/ws';
  static const int _protocolVersion = 1;

  WebSocketChannel? _channel;
  final _controller = StreamController<ServerPacket>.broadcast();
  bool _disposed = false;
  String? clientId;

  /// Connect and return a stream of decoded server packets.
  Stream<ServerPacket> connect() {
    _channel = WebSocketChannel.connect(Uri.parse(_wsUrl));

    _channel!.stream.listen(
          (raw) {
        if (_disposed) return;
        try {
          final json = jsonDecode(raw.toString()) as Map<String, dynamic>;
          final pid = json['pid'] as int;
          final data = json['d'] as Map<String, dynamic>? ?? {};

          // Auto-respond to keep-alive
          if (pid == S2C.keepAlive) {
            _sendKeepAlive(data['ts'] ?? 0);
            return;
          }

          // Track our client ID from handshake ack
          if (pid == S2C.handshakeAck) {
            clientId = data['client_id'];
            print('[WS] Connected as $clientId');
          }

          _controller.add(ServerPacket(pid, data));
        } catch (e) {
          print('[WS] Decode error: $e');
        }
      },
      onError: (e) {
        print('[WS] Error: $e');
        if (!_disposed) _controller.addError(e);
      },
      onDone: () {
        print('[WS] Connection closed.');
        if (!_disposed) _controller.close();
      },
    );

    // Send handshake immediately
    _sendHandshake();

    return _controller.stream;
  }

  // ---- Outbound packets ----

  void _send(int pid, Map<String, dynamic> data) {
    if (_channel == null || _disposed) return;
    final json = jsonEncode({'pid': pid, 'd': data});
    _channel!.sink.add(json);
  }

  void _sendHandshake() {
    _send(C2S.handshake, {
      'client_name': 'flutter-dashboard',
      'requested_state': 'SPECTATE',
      'protocol_version': _protocolVersion,
    });
    print('[WS] Handshake sent.');
  }

  void _sendKeepAlive(dynamic ts) {
    _send(C2S.keepAlive, {'ts': ts});
  }

  /// Request a module switch (only works if server granted PLAY state).
  void switchModule(String moduleId) {
    _send(C2S.moduleSwitch, {'module_id': moduleId});
  }

  /// Subscribe to specific data channels.
  void subscribe(List<String> channels) {
    _send(C2S.subscribe, {'channels': channels});
  }

  void dispose() {
    _disposed = true;
    _channel?.sink.close();
    _controller.close();
  }
}
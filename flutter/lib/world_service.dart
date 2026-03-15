import 'dart:async';
import 'dart:convert';
import 'package:web_socket_channel/web_socket_channel.dart';

class S2C {
  static const handshakeAck = 0x01, worldSnapshot = 0x02, cityUpdate = 0x03,
      agentUpdate = 0x04, economyTick = 0x05, eventLog = 0x06,
      phaseChange = 0x07, roundResult = 0x08, keepAlive = 0x09;
}
class C2S {
  static const handshake = 0x01, keepAlive = 0x02, moduleSwitch = 0x03,
      playerAction = 0x04, subscribe = 0x05;
}

class ServerPacket {
  final int pid;
  final Map<String, dynamic> data;
  ServerPacket(this.pid, this.data);
}

class WorldService {
  static const _wsUrl = 'ws://localhost:8080/ws';
  WebSocketChannel? _channel;
  final _controller = StreamController<ServerPacket>.broadcast();
  bool _disposed = false, _connecting = false;
  String? clientId;

  Stream<ServerPacket> connect() { _doConnect(); return _controller.stream; }

  void _doConnect() {
    if (_disposed || _connecting) return;
    _connecting = true;
    try { _channel = WebSocketChannel.connect(Uri.parse(_wsUrl)); } catch (e) {
      print('[WS] Connect failed: $e'); _connecting = false; _reconnect(); return;
    }
    _channel!.stream.listen((raw) {
      if (_disposed) return; _connecting = false;
      try {
        final d = jsonDecode(raw.toString());
        if (d is! Map<String, dynamic>) return;
        final pid = d['pid']; if (pid is! int) return;
        final data = d['d']; if (data is! Map<String, dynamic>) return;
        if (pid == S2C.keepAlive) { _send(C2S.keepAlive, {'ts': data['ts'] ?? 0}); return; }
        if (pid == S2C.handshakeAck) { clientId = data['client_id']?.toString(); print('[WS] Connected: $clientId'); }
        _controller.add(ServerPacket(pid, data));
      } catch (e) { print('[WS] Decode: $e'); }
    }, onError: (e) { _connecting = false; if (!_disposed) _reconnect(); },
        onDone: () { _connecting = false; if (!_disposed) _reconnect(); });
    _send(C2S.handshake, {'client_name': 'flutter', 'requested_state': 'SPECTATE', 'protocol_version': 1});
  }

  void _reconnect() { if (_disposed || _connecting) return; Future.delayed(const Duration(seconds: 3), () { if (!_disposed) _doConnect(); }); }
  void _send(int pid, Map<String, dynamic> data) { if (_channel == null || _disposed) return; try { _channel!.sink.add(jsonEncode({'pid': pid, 'd': data})); } catch (_) {} }
  void dispose() { _disposed = true; _channel?.sink.close(); }
}
import 'dart:convert';
import 'package:web_socket_channel/web_socket_channel.dart';

class MarketService {
  WebSocketChannel? _channel;
  bool _disposed = false;

  Stream<Map<String, dynamic>> connect() {
    _channel = WebSocketChannel.connect(
      Uri.parse('ws://localhost:8080/ws/observe'),
    );

    return _channel!.stream.map((data) {
      final str = data.toString().trim();
      return jsonDecode(str) as Map<String, dynamic>;
    }).handleError((error) {
      print('[WS] Error: $error');
    });
  }

  void dispose() {
    _disposed = true;
    _channel?.sink.close();
  }
}
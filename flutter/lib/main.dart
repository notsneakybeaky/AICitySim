import 'package:flutter/material.dart';
import 'dashboard.dart';

void main() {
  runApp(const SwarmApp());
}

class SwarmApp extends StatelessWidget {
  const SwarmApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Swarm Policy Engine',
      debugShowCheckedModeBanner: false,
      theme: ThemeData.dark().copyWith(
        scaffoldBackgroundColor: const Color(0xFF0A0E12),
      ),
      home: SwarmDashboard(),
    );
  }
}
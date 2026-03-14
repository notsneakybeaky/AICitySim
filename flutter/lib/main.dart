import 'package:flutter/material.dart';
import 'dashboard.dart';

void main() {
  runApp(const HyperinflationApp());
}

class HyperinflationApp extends StatelessWidget {
  const HyperinflationApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Hyperinflation',
      debugShowCheckedModeBanner: false,
      theme: ThemeData.dark().copyWith(
        scaffoldBackgroundColor: const Color(0xFF0A0E12),
      ),
      home: const WorldDashboard(),
    );
  }
}
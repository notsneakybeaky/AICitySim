import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'theme.dart';
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
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: Noir.bg,
        colorScheme: const ColorScheme.dark(
          primary: Noir.primary,
          surface: Noir.surface,
          error: Noir.rose,
        ),
        textTheme: GoogleFonts.interTextTheme(
          ThemeData.dark().textTheme,
        ),
        cardColor: Noir.surface,
      ),
      home: const WorldDashboard(),
    );
  }
}
import 'dart:ui';
import 'package:flutter/material.dart';

class Noir {
  Noir._();
  static const bg          = Color(0xFF0B0E17);
  static const surface     = Color(0xFF141825);
  static const surfaceHigh = Color(0xFF1C2133);
  static const primary     = Color(0xFF6366F1);
  static const emerald     = Color(0xFF10B981);
  static const amber       = Color(0xFFF59E0B);
  static const rose        = Color(0xFFF43F5E);
  static const cyan        = Color(0xFF22D3EE);
  static const violet      = Color(0xFF8B5CF6);
  static const textHigh    = Color(0xFFF1F5F9);
  static const textMed     = Color(0xFF94A3B8);
  static const textLow     = Color(0xFF475569);
  static const textMute    = Color(0xFF334155);

  static const Map<String, Color> agents = {
    'agent-0': Color(0xFF3B82F6), 'agent-1': Color(0xFFF43F5E),
    'agent-2': Color(0xFF8B5CF6), 'agent-3': Color(0xFF10B981),
    'agent-4': Color(0xFFF59E0B),
  };
  static Color agent(String id) => agents[id] ?? textMed;

  // City territory colors (keyed by first char of city id)
  static const Map<int, Color> cityOwnerColors = {
    110: Color(0xFF3B82F6), // 'n' nexus - blue
    105: Color(0xFFEF4444), // 'i' ironhold - red
    102: Color(0xFF10B981), // 'f' freeport - green
    101: Color(0xFFF59E0B), // 'e' eden - amber
    118: Color(0xFF8B5CF6), // 'v' vault - violet
  };

  // Terrain base colors
  static Color terrainColor(int charCode) => switch (charCode) {
    80 => const Color(0xFF1A2B1A), // P plains - dark green
    70 => const Color(0xFF0F3D1F), // F forest - deep green
    77 => const Color(0xFF3D3D3D), // M mountain - gray
    87 => const Color(0xFF0A1A3D), // W water - dark blue
    68 => const Color(0xFF3D2D1A), // D desert - brown
    85 => const Color(0xFF2A2A3D), // U urban - blue-gray
    73 => const Color(0xFF2D2A1A), // I industrial - dark amber
    _ => const Color(0xFF1A2B1A),
  };

  static const primaryGrad = LinearGradient(colors: [Color(0xFF6366F1), Color(0xFF8B5CF6)]);
}

class GlassCard extends StatelessWidget {
  final Widget child;
  final EdgeInsets padding, margin;
  final double opacity, borderRadius;
  final Color? borderColor;
  const GlassCard({super.key, required this.child, this.padding = const EdgeInsets.all(12), this.margin = EdgeInsets.zero, this.opacity = 0.06, this.borderColor, this.borderRadius = 10});
  @override
  Widget build(BuildContext context) => Container(
    margin: margin,
    padding: padding,
    decoration: BoxDecoration(
      color: Colors.white.withOpacity(opacity),
      borderRadius: BorderRadius.circular(borderRadius),
      border: Border.all(color: borderColor ?? Colors.white.withOpacity(0.08)),
    ),
    child: child,
  );
}

class GlowDot extends StatefulWidget {
  final Color color;
  final double size;
  const GlowDot({super.key, required this.color, this.size = 8});
  @override
  State<GlowDot> createState() => _GlowDotState();
}
class _GlowDotState extends State<GlowDot> with SingleTickerProviderStateMixin {
  late AnimationController _c;
  @override void initState() { super.initState(); _c = AnimationController(vsync: this, duration: const Duration(milliseconds: 1500))..repeat(reverse: true); }
  @override void dispose() { _c.dispose(); super.dispose(); }
  @override
  Widget build(BuildContext context) => AnimatedBuilder(animation: _c, builder: (_, __) {
    final g = 2.0 + _c.value * 6.0;
    return Container(width: widget.size, height: widget.size, decoration: BoxDecoration(color: widget.color, shape: BoxShape.circle, boxShadow: [BoxShadow(color: widget.color.withOpacity(0.6), blurRadius: g, spreadRadius: g * 0.3)]));
  });
}
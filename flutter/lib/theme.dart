import 'dart:ui';
import 'package:flutter/material.dart';

// =============================================================================
//  MODERN NOIR PALETTE
// =============================================================================

class Noir {
  Noir._();

  // Backgrounds
  static const bg          = Color(0xFF0F172A); // Slate 900
  static const surface     = Color(0xFF1E293B); // Slate 800
  static const surfaceHigh = Color(0xFF334155); // Slate 700
  static const overlay     = Color(0x19FFFFFF); // white 10%

  // Accent
  static const primary     = Color(0xFF6366F1); // Indigo Vivid
  static const primaryMute = Color(0x336366F1); // Indigo 20%
  static const emerald     = Color(0xFF10B981); // Success green
  static const amber       = Color(0xFFF59E0B); // Warning amber
  static const rose        = Color(0xFFF43F5E); // Danger rose
  static const cyan        = Color(0xFF22D3EE); // Info cyan
  static const violet      = Color(0xFF8B5CF6); // Subtle accent

  // Text
  static const textHigh    = Color(0xFFF8FAFC); // Slate 50
  static const textMed     = Color(0xFF94A3B8); // Slate 400
  static const textLow     = Color(0xFF475569); // Slate 600
  static const textMute    = Color(0xFF334155); // Slate 700

  // Agent palette
  static const Map<String, Color> agents = {
    'agent-0': Color(0xFF3B82F6), // blue
    'agent-1': Color(0xFFF43F5E), // rose
    'agent-2': Color(0xFF8B5CF6), // violet
    'agent-3': Color(0xFF10B981), // emerald
    'agent-4': Color(0xFFF59E0B), // amber
  };

  static Color agent(String id) => agents[id] ?? textMed;

  // Gradients
  static const primaryGrad = LinearGradient(
    colors: [Color(0xFF6366F1), Color(0xFF8B5CF6)],
  );

  static const emeraldGrad = LinearGradient(
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
    colors: [Color(0x5510B981), Color(0x0010B981)],
  );
}

// =============================================================================
//  GLASS CONTAINER
// =============================================================================

class GlassCard extends StatelessWidget {
  final Widget child;
  final EdgeInsets padding;
  final EdgeInsets margin;
  final double blur;
  final double opacity;
  final Color? borderColor;
  final double borderRadius;

  const GlassCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(16),
    this.margin = EdgeInsets.zero,
    this.blur = 12.0,
    this.opacity = 0.06,
    this.borderColor,
    this.borderRadius = 12.0,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: margin,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(borderRadius),
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: blur, sigmaY: blur),
          child: Container(
            padding: padding,
            decoration: BoxDecoration(
              color: Colors.white.withOpacity(opacity),
              borderRadius: BorderRadius.circular(borderRadius),
              border: Border.all(
                color: borderColor ?? Colors.white.withOpacity(0.08),
                width: 1,
              ),
            ),
            child: child,
          ),
        ),
      ),
    );
  }
}

// =============================================================================
//  GLOW DOT (animated pulse for status indicators)
// =============================================================================

class GlowDot extends StatefulWidget {
  final Color color;
  final double size;

  const GlowDot({super.key, required this.color, this.size = 8});

  @override
  State<GlowDot> createState() => _GlowDotState();
}

class _GlowDotState extends State<GlowDot>
    with SingleTickerProviderStateMixin {
  late AnimationController _ctrl;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1500),
    )..repeat(reverse: true);
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _ctrl,
      builder: (_, __) {
        final glow = 2.0 + _ctrl.value * 6.0;
        return Container(
          width: widget.size,
          height: widget.size,
          decoration: BoxDecoration(
            color: widget.color,
            shape: BoxShape.circle,
            boxShadow: [
              BoxShadow(
                color: widget.color.withOpacity(0.6),
                blurRadius: glow,
                spreadRadius: glow * 0.3,
              ),
            ],
          ),
        );
      },
    );
  }
}

// =============================================================================
//  STAT BAR (premium gradient bar)
// =============================================================================

class StatBar extends StatelessWidget {
  final String label;
  final double value;
  final double max;
  final Color color;

  const StatBar({
    super.key,
    required this.label,
    required this.value,
    this.max = 100,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    final pct = (value / max).clamp(0.0, 1.0);

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        children: [
          SizedBox(
            width: 105,
            child: Text(
              label,
              style: const TextStyle(
                color: Noir.textMed,
                fontSize: 11,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
          Expanded(
            child: Container(
              height: 6,
              decoration: BoxDecoration(
                color: Noir.surface,
                borderRadius: BorderRadius.circular(3),
              ),
              child: FractionallySizedBox(
                alignment: Alignment.centerLeft,
                widthFactor: pct,
                child: Container(
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(3),
                    gradient: LinearGradient(
                      colors: [
                        color.withOpacity(0.5),
                        color,
                      ],
                    ),
                    boxShadow: [
                      BoxShadow(
                        color: color.withOpacity(0.3),
                        blurRadius: 6,
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
          const SizedBox(width: 10),
          SizedBox(
            width: 38,
            child: Text(
              value.toStringAsFixed(1),
              textAlign: TextAlign.right,
              style: TextStyle(
                color: color,
                fontSize: 11,
                fontFamily: 'JetBrains Mono',
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
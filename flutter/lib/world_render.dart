import 'dart:math';
import 'package:flutter/material.dart';
import 'package:phosphor_flutter/phosphor_flutter.dart';
import 'models.dart';
import 'theme.dart';

// =============================================================================
//  SIMULATED WORLD RENDER — placeholder for future real render
// =============================================================================

class WorldRenderPanel extends StatefulWidget {
  final Map<String, City> cities;
  final List<EventEntry> events;
  final int tick;
  final String phase;

  const WorldRenderPanel({
    super.key,
    required this.cities,
    required this.events,
    required this.tick,
    required this.phase,
  });

  @override
  State<WorldRenderPanel> createState() => _WorldRenderPanelState();
}

class _WorldRenderPanelState extends State<WorldRenderPanel>
    with SingleTickerProviderStateMixin {
  late AnimationController _anim;

  @override
  void initState() {
    super.initState();
    _anim = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 4),
    )..repeat();
  }

  @override
  void dispose() {
    _anim.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 16, 8, 8),
      decoration: BoxDecoration(
        color: const Color(0xFF0B1120),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: Noir.primary.withOpacity(0.15),
          width: 1,
        ),
        boxShadow: [
          BoxShadow(
            color: Noir.primary.withOpacity(0.05),
            blurRadius: 30,
            spreadRadius: 5,
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(14),
        child: Stack(
          children: [
            // Main render canvas
            Positioned.fill(
              child: AnimatedBuilder(
                animation: _anim,
                builder: (_, __) => CustomPaint(
                  painter: _WorldMapPainter(
                    cities: widget.cities,
                    events: widget.events,
                    animValue: _anim.value,
                    tick: widget.tick,
                  ),
                ),
              ),
            ),

            // City labels overlay
            ...widget.cities.values.map((city) => _buildCityLabel(city)),

            // Corner HUD: top-left
            Positioned(
              top: 12,
              left: 14,
              child: Row(
                children: [
                  Container(
                    padding:
                    const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: Noir.primary.withOpacity(0.15),
                      borderRadius: BorderRadius.circular(6),
                      border:
                      Border.all(color: Noir.primary.withOpacity(0.25)),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          PhosphorIcons.globe(PhosphorIconsStyle.fill),
                          color: Noir.primary,
                          size: 11,
                        ),
                        const SizedBox(width: 5),
                        const Text(
                          'WORLD VIEW',
                          style: TextStyle(
                            color: Noir.primary,
                            fontSize: 9,
                            fontWeight: FontWeight.w800,
                            letterSpacing: 1.5,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 8),
                  Container(
                    padding:
                    const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
                    decoration: BoxDecoration(
                      color: Colors.white.withOpacity(0.04),
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Text(
                      'TICK ${widget.tick}',
                      style: const TextStyle(
                        color: Noir.textLow,
                        fontSize: 9,
                        fontFamily: 'JetBrains Mono',
                        letterSpacing: 1,
                      ),
                    ),
                  ),
                ],
              ),
            ),

            // Corner HUD: top-right — render target notice
            Positioned(
              top: 12,
              right: 14,
              child: Container(
                padding:
                const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: Noir.amber.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(6),
                  border: Border.all(color: Noir.amber.withOpacity(0.2)),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(PhosphorIcons.cube(PhosphorIconsStyle.fill),
                        color: Noir.amber, size: 10),
                    const SizedBox(width: 5),
                    const Text(
                      'RENDER TARGET',
                      style: TextStyle(
                        color: Noir.amber,
                        fontSize: 8,
                        fontWeight: FontWeight.w700,
                        letterSpacing: 1.5,
                      ),
                    ),
                  ],
                ),
              ),
            ),

            // Bottom-left legend
            Positioned(
              bottom: 12,
              left: 14,
              child: _buildLegend(),
            ),

            // Bottom-right region count
            Positioned(
              bottom: 12,
              right: 14,
              child: Text(
                '${widget.cities.length} CITIES  •  ${_regionCount()} REGIONS',
                style: const TextStyle(
                  color: Noir.textLow,
                  fontSize: 9,
                  fontFamily: 'JetBrains Mono',
                  letterSpacing: 1,
                ),
              ),
            ),

            // Event flash overlay
            if (widget.events.isNotEmpty && widget.phase == 'TICK_COMPLETE')
              Positioned(
                bottom: 12,
                left: 0,
                right: 0,
                child: Center(
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 12, vertical: 5),
                    decoration: BoxDecoration(
                      color: _latestEventColor().withOpacity(0.12),
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(
                          color: _latestEventColor().withOpacity(0.3)),
                    ),
                    child: Text(
                      widget.events.last.description,
                      style: TextStyle(
                        color: _latestEventColor(),
                        fontSize: 10,
                        fontWeight: FontWeight.w600,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildCityLabel(City city) {
    // Map tile coords to fractional position (grid is ~25x20)
    final xFrac = city.tileX / 25.0;
    final yFrac = city.tileY / 22.0;

    final healthColor = city.happiness >= 70
        ? Noir.emerald
        : city.happiness >= 40
        ? Noir.amber
        : Noir.rose;

    // Check if this city was targeted by a recent event
    final wasTargeted = widget.events.any((e) => e.target == city.id);

    return Positioned(
      left: 0,
      top: 0,
      right: 0,
      bottom: 0,
      child: LayoutBuilder(
        builder: (context, constraints) {
          final x = constraints.maxWidth * xFrac;
          final y = constraints.maxHeight * yFrac;

          return Stack(
            children: [
              Positioned(
                left: x + 14,
                top: y - 8,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 6, vertical: 2),
                      decoration: BoxDecoration(
                        color: (wasTargeted
                            ? _latestEventColorForCity(city.id)
                            : Noir.surface)
                            .withOpacity(0.85),
                        borderRadius: BorderRadius.circular(4),
                        border: Border.all(
                          color: wasTargeted
                              ? _latestEventColorForCity(city.id)
                              .withOpacity(0.4)
                              : Colors.white.withOpacity(0.08),
                        ),
                      ),
                      child: Text(
                        city.name.toUpperCase(),
                        style: const TextStyle(
                          color: Noir.textHigh,
                          fontSize: 9,
                          fontWeight: FontWeight.w800,
                          letterSpacing: 1,
                        ),
                      ),
                    ),
                    const SizedBox(height: 2),
                    Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Container(
                          width: 5,
                          height: 5,
                          decoration: BoxDecoration(
                            color: healthColor,
                            shape: BoxShape.circle,
                          ),
                        ),
                        const SizedBox(width: 4),
                        Text(
                          '${city.happiness.toStringAsFixed(0)}%  ${_formatPopShort(city.population)}',
                          style: const TextStyle(
                            color: Noir.textLow,
                            fontSize: 8,
                            fontFamily: 'JetBrains Mono',
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          );
        },
      ),
    );
  }

  Widget _buildLegend() {
    return Container(
      padding: const EdgeInsets.all(8),
      decoration: BoxDecoration(
        color: const Color(0xFF0B1120).withOpacity(0.85),
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: Colors.white.withOpacity(0.06)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          _legendRow(Noir.emerald, 'Stable'),
          _legendRow(Noir.amber, 'Stressed'),
          _legendRow(Noir.rose, 'Critical'),
          _legendRow(Noir.primary, 'Region Link'),
        ],
      ),
    );
  }

  Widget _legendRow(Color color, String label) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 1.5),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 8,
            height: 3,
            decoration: BoxDecoration(
              color: color,
              borderRadius: BorderRadius.circular(2),
            ),
          ),
          const SizedBox(width: 6),
          Text(
            label,
            style: const TextStyle(color: Noir.textLow, fontSize: 8),
          ),
        ],
      ),
    );
  }

  int _regionCount() {
    return widget.cities.values.map((c) => c.regionId).toSet().length;
  }

  Color _latestEventColor() {
    if (widget.events.isEmpty) return Noir.textLow;
    final type = widget.events.last.type;
    if (type.contains('ATTACK') || type.contains('SABOTAGE')) return Noir.rose;
    if (type.contains('BUILD') || type.contains('BOOST')) return Noir.emerald;
    if (type.contains('BID') || type.contains('DRAIN')) return Noir.amber;
    if (type.contains('INFILTRATE') || type.contains('PROPAGANDA')) {
      return Noir.violet;
    }
    return Noir.cyan;
  }

  Color _latestEventColorForCity(String cityId) {
    final evt = widget.events.lastWhere(
          (e) => e.target == cityId,
      orElse: () => EventEntry(type: '', description: ''),
    );
    if (evt.type.isEmpty) return Noir.surface;
    return _latestEventColor();
  }

  String _formatPopShort(int pop) {
    if (pop >= 1000000) return '${(pop / 1000000).toStringAsFixed(1)}M';
    if (pop >= 1000) return '${(pop / 1000).toStringAsFixed(0)}K';
    return '$pop';
  }
}

// =============================================================================
//  WORLD MAP PAINTER — grid, nodes, connections, events, scan line
// =============================================================================

class _WorldMapPainter extends CustomPainter {
  final Map<String, City> cities;
  final List<EventEntry> events;
  final double animValue;
  final int tick;

  _WorldMapPainter({
    required this.cities,
    required this.events,
    required this.animValue,
    required this.tick,
  });

  // Region definitions for connection lines
  static const _regionGroups = {
    'alpha': ['nexus', 'ironhold'],
    'beta': ['freeport', 'eden'],
    'gamma': ['vault'],
  };

  static const _regionColors = {
    'alpha': Noir.cyan,
    'beta': Noir.emerald,
    'gamma': Noir.amber,
  };

  @override
  void paint(Canvas canvas, Size size) {
    _drawGrid(canvas, size);
    _drawRegionZones(canvas, size);
    _drawConnections(canvas, size);
    _drawEventRings(canvas, size);
    _drawCityNodes(canvas, size);
    _drawScanLine(canvas, size);
  }

  Offset _cityPos(City city, Size size) {
    return Offset(
      (city.tileX / 25.0) * size.width,
      (city.tileY / 22.0) * size.height,
    );
  }

  // ---- Grid ----

  void _drawGrid(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = Colors.white.withOpacity(0.02)
      ..strokeWidth = 0.5;

    const spacing = 30.0;
    for (double x = 0; x < size.width; x += spacing) {
      canvas.drawLine(Offset(x, 0), Offset(x, size.height), paint);
    }
    for (double y = 0; y < size.height; y += spacing) {
      canvas.drawLine(Offset(0, y), Offset(size.width, y), paint);
    }

    // Subtle cross at center
    final centerPaint = Paint()
      ..color = Colors.white.withOpacity(0.03)
      ..strokeWidth = 1;
    canvas.drawLine(
      Offset(size.width / 2, 0),
      Offset(size.width / 2, size.height),
      centerPaint,
    );
    canvas.drawLine(
      Offset(0, size.height / 2),
      Offset(size.width, size.height / 2),
      centerPaint,
    );
  }

  // ---- Region zones (subtle background shading) ----

  void _drawRegionZones(Canvas canvas, Size size) {
    for (final entry in _regionGroups.entries) {
      final regionId = entry.key;
      final cityIds = entry.value;
      final color = _regionColors[regionId] ?? Noir.textMute;

      final positions = <Offset>[];
      for (final id in cityIds) {
        final city = cities[id];
        if (city != null) positions.add(_cityPos(city, size));
      }

      if (positions.length >= 2) {
        // Draw a subtle hull around the region
        final center = positions.reduce((a, b) => a + b) /
            positions.length.toDouble();
        final radius = positions
            .map((p) => (p - center).distance)
            .reduce((a, b) => a > b ? a : b) +
            50;

        canvas.drawCircle(
          center,
          radius,
          Paint()
            ..color = color.withOpacity(0.03)
            ..style = PaintingStyle.fill,
        );
        canvas.drawCircle(
          center,
          radius,
          Paint()
            ..color = color.withOpacity(0.06)
            ..style = PaintingStyle.stroke
            ..strokeWidth = 0.5,
        );
      } else if (positions.length == 1) {
        canvas.drawCircle(
          positions[0],
          45,
          Paint()
            ..color = color.withOpacity(0.03)
            ..style = PaintingStyle.fill,
        );
      }
    }
  }

  // ---- Connection lines between same-region cities ----

  void _drawConnections(Canvas canvas, Size size) {
    for (final entry in _regionGroups.entries) {
      final regionId = entry.key;
      final cityIds = entry.value;
      final color = _regionColors[regionId] ?? Noir.primary;

      for (int i = 0; i < cityIds.length; i++) {
        for (int j = i + 1; j < cityIds.length; j++) {
          final cityA = cities[cityIds[i]];
          final cityB = cities[cityIds[j]];
          if (cityA == null || cityB == null) continue;

          final posA = _cityPos(cityA, size);
          final posB = _cityPos(cityB, size);

          // Animated dash
          final path = Path()
            ..moveTo(posA.dx, posA.dy)
            ..lineTo(posB.dx, posB.dy);

          _drawDashedLine(canvas, posA, posB, color.withOpacity(0.2), 2, 6);

          // Traveling dot along the line
          final t = animValue;
          final dotPos = Offset(
            posA.dx + (posB.dx - posA.dx) * t,
            posA.dy + (posB.dy - posA.dy) * t,
          );
          canvas.drawCircle(
            dotPos,
            2.5,
            Paint()..color = color.withOpacity(0.6),
          );
          canvas.drawCircle(
            dotPos,
            5,
            Paint()..color = color.withOpacity(0.15),
          );
        }
      }
    }
  }

  void _drawDashedLine(
      Canvas canvas, Offset a, Offset b, Color color, double width, double gap) {
    final paint = Paint()
      ..color = color
      ..strokeWidth = width
      ..strokeCap = StrokeCap.round;

    final dx = b.dx - a.dx;
    final dy = b.dy - a.dy;
    final dist = sqrt(dx * dx + dy * dy);
    final dashLen = gap;
    final steps = (dist / (dashLen * 2)).floor();

    for (int i = 0; i < steps; i++) {
      final startT = (i * dashLen * 2) / dist;
      final endT = ((i * dashLen * 2) + dashLen) / dist;
      canvas.drawLine(
        Offset(a.dx + dx * startT, a.dy + dy * startT),
        Offset(a.dx + dx * endT, a.dy + dy * endT),
        paint,
      );
    }
  }

  // ---- City nodes ----

  void _drawCityNodes(Canvas canvas, Size size) {
    for (final city in cities.values) {
      final pos = _cityPos(city, size);

      final healthColor = city.happiness >= 70
          ? Noir.emerald
          : city.happiness >= 40
          ? Noir.amber
          : Noir.rose;

      // Population-based size
      final baseRadius =
          6.0 + (log(max(1, city.population)) / log(10)) * 1.5;
      final pulse = 1.0 + sin(animValue * 2 * pi) * 0.12;
      final radius = baseRadius * pulse;

      // Outer glow
      canvas.drawCircle(
        pos,
        radius + 10,
        Paint()
          ..color = healthColor.withOpacity(0.06 + animValue * 0.03)
          ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 8),
      );

      // Mid ring
      canvas.drawCircle(
        pos,
        radius + 4,
        Paint()
          ..color = healthColor.withOpacity(0.08)
          ..style = PaintingStyle.stroke
          ..strokeWidth = 0.5,
      );

      // Core
      canvas.drawCircle(
        pos,
        radius,
        Paint()..color = healthColor.withOpacity(0.2),
      );
      canvas.drawCircle(
        pos,
        radius * 0.6,
        Paint()..color = healthColor.withOpacity(0.8),
      );

      // Defense ring (if high defenses)
      if (city.digitalDefenses > 60) {
        final defenseAngle = animValue * 2 * pi;
        canvas.drawArc(
          Rect.fromCircle(center: pos, radius: radius + 7),
          defenseAngle,
          pi * 0.6,
          false,
          Paint()
            ..color = Noir.cyan.withOpacity(0.3)
            ..style = PaintingStyle.stroke
            ..strokeWidth = 1.5
            ..strokeCap = StrokeCap.round,
        );
      }
    }
  }

  // ---- Event rings (expanding circles on targeted cities) ----

  void _drawEventRings(Canvas canvas, Size size) {
    for (final evt in events) {
      if (evt.target == null) continue;
      final city = cities[evt.target];
      if (city == null) continue;

      final pos = _cityPos(city, size);
      final color = _evtColor(evt.type);

      // Expanding ring
      final ringRadius = 15.0 + animValue * 40.0;
      final ringOpacity = (1.0 - animValue) * 0.4;

      canvas.drawCircle(
        pos,
        ringRadius,
        Paint()
          ..color = color.withOpacity(ringOpacity.clamp(0.0, 1.0))
          ..style = PaintingStyle.stroke
          ..strokeWidth = 2,
      );

      // Second ring offset
      final ring2Anim = (animValue + 0.3) % 1.0;
      final ring2Radius = 15.0 + ring2Anim * 40.0;
      final ring2Opacity = (1.0 - ring2Anim) * 0.2;

      canvas.drawCircle(
        pos,
        ring2Radius,
        Paint()
          ..color = color.withOpacity(ring2Opacity.clamp(0.0, 1.0))
          ..style = PaintingStyle.stroke
          ..strokeWidth = 1,
      );
    }

    // Draw attack arcs (agent → city)
    for (final evt in events) {
      if (evt.agent == null || evt.target == null) continue;
      final targetCity = cities[evt.target];
      if (targetCity == null) continue;

      final color = _evtColor(evt.type);
      final targetPos = _cityPos(targetCity, size);

      // Agent source position (approximate: spread them vertically on the left)
      final agentIndex = int.tryParse(evt.agent!.replaceAll('agent-', '')) ?? 0;
      final sourcePos = Offset(
        size.width * 0.05,
        size.height * (0.15 + agentIndex * 0.17),
      );

      // Animated projectile along arc
      final t = animValue;
      final mid = Offset(
        (sourcePos.dx + targetPos.dx) / 2,
        min(sourcePos.dy, targetPos.dy) - 30,
      );

      final projX = _quadBezier(sourcePos.dx, mid.dx, targetPos.dx, t);
      final projY = _quadBezier(sourcePos.dy, mid.dy, targetPos.dy, t);

      // Trail
      for (int i = 0; i < 5; i++) {
        final tt = (t - i * 0.03).clamp(0.0, 1.0);
        final tx = _quadBezier(sourcePos.dx, mid.dx, targetPos.dx, tt);
        final ty = _quadBezier(sourcePos.dy, mid.dy, targetPos.dy, tt);
        canvas.drawCircle(
          Offset(tx, ty),
          2.0 - i * 0.3,
          Paint()..color = color.withOpacity(0.3 - i * 0.05),
        );
      }

      canvas.drawCircle(
        Offset(projX, projY),
        3,
        Paint()..color = color.withOpacity(0.7),
      );
    }
  }

  double _quadBezier(double p0, double p1, double p2, double t) {
    return (1 - t) * (1 - t) * p0 + 2 * (1 - t) * t * p1 + t * t * p2;
  }

  // ---- Scan line ----

  void _drawScanLine(Canvas canvas, Size size) {
    final y = animValue * size.height;

    final paint = Paint()
      ..shader = LinearGradient(
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
        colors: [
          Colors.white.withOpacity(0.0),
          Noir.primary.withOpacity(0.03),
          Colors.white.withOpacity(0.0),
        ],
      ).createShader(Rect.fromLTWH(0, y - 30, size.width, 60));

    canvas.drawRect(
      Rect.fromLTWH(0, y - 30, size.width, 60),
      paint,
    );

    // Thin line
    canvas.drawLine(
      Offset(0, y),
      Offset(size.width, y),
      Paint()
        ..color = Noir.primary.withOpacity(0.08)
        ..strokeWidth = 1,
    );
  }

  Color _evtColor(String type) {
    if (type.contains('ATTACK') || type.contains('SABOTAGE') ||
        type.contains('UNREST')) {
      return Noir.rose;
    }
    if (type.contains('BUILD') || type.contains('BOOST') ||
        type.contains('DEFEND') || type.contains('INJECT')) {
      return Noir.emerald;
    }
    if (type.contains('BID') || type.contains('DRAIN')) return Noir.amber;
    if (type.contains('INFILTRATE') || type.contains('PROPAGANDA')) {
      return Noir.violet;
    }
    return Noir.cyan;
  }

  @override
  bool shouldRepaint(covariant _WorldMapPainter old) => true;
}
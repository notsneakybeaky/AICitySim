import 'dart:math';
import 'package:flutter/material.dart';
import 'package:phosphor_flutter/phosphor_flutter.dart';
import 'models.dart';
import 'theme.dart';

// =============================================================================
//  WORLD RENDER PANEL
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

  /// Build region groups dynamically from city data.
  Map<String, List<String>> _buildRegionGroups() {
    final groups = <String, List<String>>{};
    for (final city in widget.cities.values) {
      final r = city.regionId.isNotEmpty ? city.regionId : 'default';
      groups.putIfAbsent(r, () => []).add(city.id);
    }
    return groups;
  }

  static final _regionPalette = [
    Noir.cyan,
    Noir.emerald,
    Noir.amber,
    Noir.violet,
    Noir.rose,
    Noir.primary,
  ];

  Color _regionColor(String regionId) {
    return _regionPalette[regionId.hashCode.abs() % _regionPalette.length];
  }

  @override
  Widget build(BuildContext context) {
    final hasData = widget.cities.isNotEmpty;

    return Container(
      margin: const EdgeInsets.fromLTRB(16, 16, 8, 8),
      decoration: BoxDecoration(
        color: const Color(0xFF0B1120),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: Noir.primary.withOpacity(0.15), width: 1),
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
            // Canvas
            Positioned.fill(
              child: AnimatedBuilder(
                animation: _anim,
                builder: (_, __) => CustomPaint(
                  painter: _WorldMapPainter(
                    cities: widget.cities,
                    events: widget.events,
                    regionGroups: _buildRegionGroups(),
                    regionColorFn: _regionColor,
                    animValue: _anim.value,
                    tick: widget.tick,
                    hasData: hasData,
                  ),
                ),
              ),
            ),

            // City labels (only when data exists)
            if (hasData)
              ...widget.cities.values.map((city) => _buildCityLabel(city)),

            // ---- Top-left HUD ----
            Positioned(
              top: 12,
              left: 14,
              child: Row(
                children: [
                  _hudBadge(
                    PhosphorIcons.globe(PhosphorIconsStyle.fill),
                    'WORLD VIEW',
                    Noir.primary,
                  ),
                  const SizedBox(width: 8),
                  Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 6, vertical: 4),
                    decoration: BoxDecoration(
                      color: Colors.white.withOpacity(0.04),
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Text('TICK ${widget.tick}',
                        style: const TextStyle(
                            color: Noir.textLow,
                            fontSize: 9,
                            fontFamily: 'JetBrains Mono',
                            letterSpacing: 1)),
                  ),
                ],
              ),
            ),

            // ---- Top-right: render target ----
            Positioned(
              top: 12,
              right: 14,
              child: _hudBadge(
                PhosphorIcons.cube(PhosphorIconsStyle.fill),
                'RENDER TARGET',
                Noir.amber,
              ),
            ),

            // ---- Bottom-left: legend ----
            Positioned(bottom: 12, left: 14, child: _buildLegend()),

            // ---- Bottom-right: stats ----
            Positioned(
              bottom: 12,
              right: 14,
              child: Text(
                hasData
                    ? '${widget.cities.length} CITIES  •  ${_buildRegionGroups().length} REGIONS'
                    : 'AWAITING DATA',
                style: const TextStyle(
                    color: Noir.textLow,
                    fontSize: 9,
                    fontFamily: 'JetBrains Mono',
                    letterSpacing: 1),
              ),
            ),

            // ---- Center notice when no data ----
            if (!hasData)
              Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(PhosphorIcons.globe(PhosphorIconsStyle.duotone),
                        color: Noir.primary.withOpacity(0.15), size: 64),
                    const SizedBox(height: 12),
                    const Text('AWAITING WORLD DATA',
                        style: TextStyle(
                            color: Noir.textLow,
                            fontSize: 12,
                            fontWeight: FontWeight.w700,
                            letterSpacing: 3)),
                    const SizedBox(height: 4),
                    Text(
                        'Connect to simulation server to see the world render',
                        style: TextStyle(
                            color: Noir.textMute, fontSize: 10)),
                  ],
                ),
              ),

            // ---- Latest event flash ----
            if (widget.events.isNotEmpty && widget.phase == 'TICK_COMPLETE')
              Positioned(
                bottom: 30,
                left: 60,
                right: 60,
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
                          fontWeight: FontWeight.w600),
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

  Widget _hudBadge(IconData icon, String text, Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: color.withOpacity(0.2)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, color: color, size: 10),
          const SizedBox(width: 5),
          Text(text,
              style: TextStyle(
                  color: color,
                  fontSize: 8,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 1.5)),
        ],
      ),
    );
  }

  Widget _buildCityLabel(City city) {
    final xFrac = (city.tileX / 25.0).clamp(0.05, 0.9);
    final yFrac = (city.tileY / 22.0).clamp(0.05, 0.9);

    final healthColor = city.happiness >= 70
        ? Noir.emerald
        : city.happiness >= 40
        ? Noir.amber
        : Noir.rose;

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
                            ? _eventColorForCity(city.id)
                            : Noir.surface)
                            .withOpacity(0.85),
                        borderRadius: BorderRadius.circular(4),
                        border: Border.all(
                          color: wasTargeted
                              ? _eventColorForCity(city.id).withOpacity(0.4)
                              : Colors.white.withOpacity(0.08),
                        ),
                      ),
                      child: Text(city.name.toUpperCase(),
                          style: const TextStyle(
                              color: Noir.textHigh,
                              fontSize: 9,
                              fontWeight: FontWeight.w800,
                              letterSpacing: 1)),
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
                                shape: BoxShape.circle)),
                        const SizedBox(width: 4),
                        Text(
                          '${city.happiness.toStringAsFixed(0)}%  ${_fmtPop(city.population)}',
                          style: const TextStyle(
                              color: Noir.textLow,
                              fontSize: 8,
                              fontFamily: 'JetBrains Mono'),
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
          _legendItem(Noir.emerald, 'Stable (>70)'),
          _legendItem(Noir.amber, 'Stressed (40-70)'),
          _legendItem(Noir.rose, 'Critical (<40)'),
          _legendItem(Noir.primary, 'Region link'),
          _legendItem(Noir.cyan, 'Defense ring'),
        ],
      ),
    );
  }

  Widget _legendItem(Color color, String label) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 1.5),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
              width: 8,
              height: 3,
              decoration: BoxDecoration(
                  color: color, borderRadius: BorderRadius.circular(2))),
          const SizedBox(width: 6),
          Text(label,
              style: const TextStyle(color: Noir.textLow, fontSize: 8)),
        ],
      ),
    );
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

  Color _eventColorForCity(String cityId) {
    final evt = widget.events.lastWhere(
          (e) => e.target == cityId,
      orElse: () => EventEntry(type: '', description: ''),
    );
    if (evt.type.isEmpty) return Noir.surface;
    return _latestEventColor();
  }

  String _fmtPop(int pop) {
    if (pop >= 1000000) return '${(pop / 1000000).toStringAsFixed(1)}M';
    if (pop >= 1000) return '${(pop / 1000).toStringAsFixed(0)}K';
    return '$pop';
  }
}

// =============================================================================
//  WORLD MAP PAINTER
// =============================================================================

class _WorldMapPainter extends CustomPainter {
  final Map<String, City> cities;
  final List<EventEntry> events;
  final Map<String, List<String>> regionGroups;
  final Color Function(String) regionColorFn;
  final double animValue;
  final int tick;
  final bool hasData;

  _WorldMapPainter({
    required this.cities,
    required this.events,
    required this.regionGroups,
    required this.regionColorFn,
    required this.animValue,
    required this.tick,
    required this.hasData,
  });

  @override
  void paint(Canvas canvas, Size size) {
    _drawGrid(canvas, size);

    if (hasData) {
      _drawRegionZones(canvas, size);
      _drawConnections(canvas, size);
      _drawEventRings(canvas, size);
      _drawCityNodes(canvas, size);
      _drawAgentPositions(canvas, size);
    } else {
      _drawDemoNodes(canvas, size);
    }

    _drawScanLine(canvas, size);
  }

  Offset _cityPos(City city, Size size) {
    final x = (city.tileX / 25.0).clamp(0.05, 0.95);
    final y = (city.tileY / 22.0).clamp(0.05, 0.95);
    return Offset(x * size.width, y * size.height);
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

    final center = Paint()
      ..color = Colors.white.withOpacity(0.03)
      ..strokeWidth = 1;
    canvas.drawLine(
        Offset(size.width / 2, 0), Offset(size.width / 2, size.height), center);
    canvas.drawLine(
        Offset(0, size.height / 2), Offset(size.width, size.height / 2), center);
  }

  // ---- Demo nodes (when no real data) ----

  void _drawDemoNodes(Canvas canvas, Size size) {
    final rng = Random(42);
    for (int i = 0; i < 8; i++) {
      final x = 0.1 + rng.nextDouble() * 0.8;
      final y = 0.1 + rng.nextDouble() * 0.8;
      final pos = Offset(x * size.width, y * size.height);
      final pulse = 1.0 + sin((animValue + i * 0.12) * 2 * pi) * 0.15;
      final r = 5.0 * pulse;

      canvas.drawCircle(
          pos,
          r + 8,
          Paint()
            ..color = Noir.primary.withOpacity(0.04)
            ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 6));
      canvas.drawCircle(
          pos, r, Paint()..color = Noir.primary.withOpacity(0.08));
      canvas.drawCircle(
          pos, r * 0.5, Paint()..color = Noir.primary.withOpacity(0.2));
    }

    // Demo connections
    for (int i = 0; i < 4; i++) {
      final a = Offset(
          (0.1 + rng.nextDouble() * 0.8) * size.width,
          (0.1 + rng.nextDouble() * 0.8) * size.height);
      final b = Offset(
          (0.1 + rng.nextDouble() * 0.8) * size.width,
          (0.1 + rng.nextDouble() * 0.8) * size.height);
      _drawDashedLine(canvas, a, b, Noir.primary.withOpacity(0.06), 1, 6);
    }
  }

  // ---- Region zones ----

  void _drawRegionZones(Canvas canvas, Size size) {
    for (final entry in regionGroups.entries) {
      final regionId = entry.key;
      final cityIds = entry.value;
      final color = regionColorFn(regionId);

      final positions = <Offset>[];
      for (final id in cityIds) {
        final city = cities[id];
        if (city != null) positions.add(_cityPos(city, size));
      }

      if (positions.length >= 2) {
        final center = positions.reduce((a, b) => a + b) /
            positions.length.toDouble();
        final radius = positions
            .map((p) => (p - center).distance)
            .reduce((a, b) => a > b ? a : b) +
            50;

        canvas.drawCircle(center, radius,
            Paint()..color = color.withOpacity(0.03));
        canvas.drawCircle(
            center,
            radius,
            Paint()
              ..color = color.withOpacity(0.06)
              ..style = PaintingStyle.stroke
              ..strokeWidth = 0.5);
      } else if (positions.length == 1) {
        canvas.drawCircle(positions[0], 45,
            Paint()..color = color.withOpacity(0.03));
      }
    }
  }

  // ---- Connections ----

  void _drawConnections(Canvas canvas, Size size) {
    for (final entry in regionGroups.entries) {
      final regionId = entry.key;
      final cityIds = entry.value;
      final color = regionColorFn(regionId);

      for (int i = 0; i < cityIds.length; i++) {
        for (int j = i + 1; j < cityIds.length; j++) {
          final cityA = cities[cityIds[i]];
          final cityB = cities[cityIds[j]];
          if (cityA == null || cityB == null) continue;

          final posA = _cityPos(cityA, size);
          final posB = _cityPos(cityB, size);

          _drawDashedLine(canvas, posA, posB, color.withOpacity(0.2), 1.5, 6);

          // Traveling dot
          final dotPos = Offset(
            posA.dx + (posB.dx - posA.dx) * animValue,
            posA.dy + (posB.dy - posA.dy) * animValue,
          );
          canvas.drawCircle(dotPos, 2.5, Paint()..color = color.withOpacity(0.6));
          canvas.drawCircle(dotPos, 5, Paint()..color = color.withOpacity(0.12));
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
    if (dist == 0) return;
    final steps = (dist / (gap * 2)).floor();

    for (int i = 0; i < steps; i++) {
      final startT = (i * gap * 2) / dist;
      final endT = ((i * gap * 2) + gap) / dist;
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
            ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 8));

      // Mid ring
      canvas.drawCircle(
          pos,
          radius + 4,
          Paint()
            ..color = healthColor.withOpacity(0.08)
            ..style = PaintingStyle.stroke
            ..strokeWidth = 0.5);

      // Core
      canvas.drawCircle(pos, radius, Paint()..color = healthColor.withOpacity(0.2));
      canvas.drawCircle(
          pos, radius * 0.6, Paint()..color = healthColor.withOpacity(0.8));

      // Defense ring
      if (city.digitalDefenses > 60) {
        final angle = animValue * 2 * pi;
        canvas.drawArc(
          Rect.fromCircle(center: pos, radius: radius + 7),
          angle,
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

  // ---- Agent positions (left side of map) ----

  void _drawAgentPositions(Canvas canvas, Size size) {
    final agentIds = ['agent-0', 'agent-1', 'agent-2', 'agent-3', 'agent-4'];
    final spacing = size.height / (agentIds.length + 1);

    for (int i = 0; i < agentIds.length; i++) {
      final id = agentIds[i];
      final color = Noir.agents[id] ?? Noir.textMed;
      final y = spacing * (i + 1);
      final x = size.width * 0.04;
      final pos = Offset(x, y);

      // Check if this agent has events this tick
      final agentEvts = events.where((e) => e.agent == id).toList();
      final isActive = agentEvts.isNotEmpty;

      // Agent dot
      final dotRadius = isActive ? 5.0 : 3.0;
      final pulseR = isActive ? dotRadius + sin(animValue * 2 * pi) * 2 : dotRadius;

      if (isActive) {
        canvas.drawCircle(
            pos,
            pulseR + 6,
            Paint()
              ..color = color.withOpacity(0.1)
              ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 4));
      }

      canvas.drawCircle(pos, pulseR, Paint()..color = color.withOpacity(isActive ? 0.9 : 0.3));

      // Draw attack arcs from agent to target cities
      for (final evt in agentEvts) {
        if (evt.target == null) continue;
        final targetCity = cities[evt.target];
        if (targetCity == null) continue;

        final targetPos = _cityPos(targetCity, size);
        final evtColor = _evtColor(evt.type);

        // Bezier arc
        final mid = Offset(
          (pos.dx + targetPos.dx) / 2,
          min(pos.dy, targetPos.dy) - 30,
        );

        // Projectile
        final t = animValue;
        final projX = _quadBezier(pos.dx, mid.dx, targetPos.dx, t);
        final projY = _quadBezier(pos.dy, mid.dy, targetPos.dy, t);

        // Trail
        for (int j = 0; j < 5; j++) {
          final tt = (t - j * 0.03).clamp(0.0, 1.0);
          final tx = _quadBezier(pos.dx, mid.dx, targetPos.dx, tt);
          final ty = _quadBezier(pos.dy, mid.dy, targetPos.dy, tt);
          canvas.drawCircle(
            Offset(tx, ty),
            2.0 - j * 0.3,
            Paint()..color = evtColor.withOpacity(0.3 - j * 0.05),
          );
        }

        canvas.drawCircle(
            Offset(projX, projY), 3, Paint()..color = evtColor.withOpacity(0.7));
      }
    }
  }

  double _quadBezier(double p0, double p1, double p2, double t) {
    return (1 - t) * (1 - t) * p0 + 2 * (1 - t) * t * p1 + t * t * p2;
  }

  // ---- Event rings ----

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
            ..strokeWidth = 2);

      // Second ring (offset phase)
      final ring2Anim = (animValue + 0.3) % 1.0;
      canvas.drawCircle(
          pos,
          15.0 + ring2Anim * 40.0,
          Paint()
            ..color = color.withOpacity(((1.0 - ring2Anim) * 0.2).clamp(0.0, 1.0))
            ..style = PaintingStyle.stroke
            ..strokeWidth = 1);
    }
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
          Noir.primary.withOpacity(hasData ? 0.03 : 0.015),
          Colors.white.withOpacity(0.0),
        ],
      ).createShader(Rect.fromLTWH(0, y - 30, size.width, 60));

    canvas.drawRect(Rect.fromLTWH(0, y - 30, size.width, 60), paint);

    canvas.drawLine(
        Offset(0, y),
        Offset(size.width, y),
        Paint()
          ..color = Noir.primary.withOpacity(0.08)
          ..strokeWidth = 1);
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
import 'dart:math';
import 'package:flutter/material.dart';
import 'package:phosphor_flutter/phosphor_flutter.dart';
import 'models.dart';
import 'theme.dart';

// =============================================================================
//  WORLD RENDER PANEL — Strategic Map View
//
//  Design goals:
//    1. Cities are the heroes — big, clear, stat-readable nodes
//    2. Agents shown AT their actual city (not some sidebar)
//    3. Actions shown as arcs between source agent → target city
//    4. Color = health status at a glance (green/amber/red)
//    5. Minimal animation — only where it conveys live info
// =============================================================================

class WorldRenderPanel extends StatefulWidget {
  final Map<String, City> cities;
  final List<EventEntry> events;
  final int tick;
  final String phase;
  final Map<String, String> agentLocations;

  const WorldRenderPanel({
    super.key,
    required this.cities,
    required this.events,
    required this.tick,
    required this.phase,
    this.agentLocations = const {},
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
      duration: const Duration(seconds: 3),
    )..repeat();
  }

  @override
  void dispose() {
    _anim.dispose();
    super.dispose();
  }

  // City positions — spread across the panel in a readable layout
  // Using fixed fractional positions so they don't overlap
  static const _cityLayout = <String, List<double>>{
    'nexus':    [0.50, 0.30], // center-top (capital)
    'ironhold': [0.20, 0.55], // left-mid
    'freeport': [0.80, 0.22], // right-top
    'eden':     [0.72, 0.70], // right-bottom
    'vault':    [0.15, 0.20], // left-top
  };

  Offset _cityPos(String cityId, Size size) {
    final frac = _cityLayout[cityId] ?? [0.5, 0.5];
    return Offset(frac[0] * size.width, frac[1] * size.height);
  }

  Color _healthColor(double happiness) {
    if (happiness >= 65) return Noir.emerald;
    if (happiness >= 40) return Noir.amber;
    return Noir.rose;
  }

  Color _evtColor(String type) {
    if (type.contains('ATTACK') || type.contains('SABOTAGE') ||
        type.contains('UNREST') || type.contains('DAMAGE')) {
      return Noir.rose;
    }
    if (type.contains('BUILD') || type.contains('BOOST') ||
        type.contains('DEFEND') || type.contains('INJECT')) {
      return Noir.emerald;
    }
    if (type.contains('BID') || type.contains('ASK') ||
        type.contains('DRAIN') || type.contains('EARN')) {
      return Noir.amber;
    }
    if (type.contains('INFILTRATE') || type.contains('PROPAGANDA')) {
      return Noir.violet;
    }
    if (type.contains('MOVE')) return Noir.cyan;
    if (type.contains('ALLIANCE')) return Noir.cyan;
    return Noir.textLow;
  }

  @override
  Widget build(BuildContext context) {
    final hasData = widget.cities.isNotEmpty;

    return Container(
      margin: const EdgeInsets.fromLTRB(16, 16, 8, 8),
      decoration: BoxDecoration(
        color: const Color(0xFF0A0F1E),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: Colors.white.withOpacity(0.06), width: 1),
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(14),
        child: hasData ? _buildLiveMap() : _buildEmptyState(),
      ),
    );
  }

  Widget _buildEmptyState() {
    return Center(
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
          Text('Connect to simulation server to begin',
              style: TextStyle(color: Noir.textMute, fontSize: 10)),
        ],
      ),
    );
  }

  Widget _buildLiveMap() {
    return Stack(
      children: [
        // Canvas layer — connections + action arcs
        Positioned.fill(
          child: AnimatedBuilder(
            animation: _anim,
            builder: (_, __) => CustomPaint(
              painter: _MapPainter(
                cities: widget.cities,
                events: widget.events,
                cityPosFn: _cityPos,
                evtColorFn: _evtColor,
                agentLocations: widget.agentLocations,
                animValue: _anim.value,
              ),
            ),
          ),
        ),

        // City nodes — Flutter widgets for crisp text
        ...widget.cities.entries.map((e) => _buildCityNode(e.key, e.value)),

        // Agent badges at their cities
        ..._buildAgentBadges(),

        // Top-left: phase + tick
        Positioned(
          top: 10,
          left: 12,
          child: _phaseIndicator(),
        ),

        // Bottom: event ticker
        if (widget.events.isNotEmpty && widget.phase == 'TICK_COMPLETE')
          Positioned(
            bottom: 10,
            left: 12,
            right: 12,
            child: _eventTicker(),
          ),
      ],
    );
  }

  // ================================================================
  //  CITY NODE — the main information display
  // ================================================================

  Widget _buildCityNode(String id, City city) {
    final color = _healthColor(city.happiness);
    final isUnderAttack = widget.events.any(
            (e) => e.target == id && (e.type.contains('ATTACK') ||
            e.type.contains('SABOTAGE') || e.type.contains('DAMAGE')));
    final isBeingBuilt = widget.events.any(
            (e) => e.target == id && (e.type.contains('BUILD') ||
            e.type.contains('BOOST') || e.type.contains('DEFEND')));

    return Positioned(
      left: 0, top: 0, right: 0, bottom: 0,
      child: LayoutBuilder(builder: (context, constraints) {
        final pos = _cityPos(id, Size(constraints.maxWidth, constraints.maxHeight));
        // Offset so the card is centered on the position
        const cardW = 140.0;
        const cardH = 88.0;

        return Stack(children: [
          Positioned(
            left: pos.dx - cardW / 2,
            top: pos.dy - cardH / 2,
            width: cardW,
            child: _CityCard(
              city: city,
              color: color,
              isUnderAttack: isUnderAttack,
              isBeingBuilt: isBeingBuilt,
            ),
          ),
        ]);
      }),
    );
  }

  // ================================================================
  //  AGENT BADGES — colored dots at their city
  // ================================================================

  List<Widget> _buildAgentBadges() {
    // Group agents by city
    final Map<String, List<String>> cityAgents = {};
    for (final entry in widget.agentLocations.entries) {
      cityAgents.putIfAbsent(entry.value, () => []).add(entry.key);
    }

    final widgets = <Widget>[];
    for (final entry in cityAgents.entries) {
      final cityId = entry.key;
      final agentIds = entry.value;
      if (!widget.cities.containsKey(cityId)) continue;

      widgets.add(Positioned(
        left: 0, top: 0, right: 0, bottom: 0,
        child: LayoutBuilder(builder: (context, constraints) {
          final size = Size(constraints.maxWidth, constraints.maxHeight);
          final pos = _cityPos(cityId, size);
          // Place agent row below the city card
          final baseX = pos.dx - (agentIds.length * 14.0) / 2;
          final baseY = pos.dy + 50;

          return Stack(
            children: [
              for (int i = 0; i < agentIds.length; i++)
                Positioned(
                  left: baseX + i * 14.0,
                  top: baseY,
                  child: _AgentDot(
                    agentId: agentIds[i],
                    hasEvent: widget.events.any((e) => e.agent == agentIds[i]),
                  ),
                ),
            ],
          );
        }),
      ));
    }
    return widgets;
  }

  // ================================================================
  //  PHASE INDICATOR
  // ================================================================

  Widget _phaseIndicator() {
    final isLive = widget.phase == 'COLLECTING' || widget.phase == 'PROCESSING';
    final color = isLive ? Noir.cyan : Noir.textLow;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: const Color(0xFF0A0F1E).withOpacity(0.9),
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: color.withOpacity(0.25)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (isLive)
            Container(
              width: 6, height: 6, margin: const EdgeInsets.only(right: 6),
              decoration: BoxDecoration(
                color: color, shape: BoxShape.circle,
                boxShadow: [BoxShadow(color: color.withOpacity(0.5), blurRadius: 6)],
              ),
            ),
          Text(
            'TICK ${widget.tick}',
            style: TextStyle(
              color: color,
              fontSize: 10,
              fontWeight: FontWeight.w700,
              fontFamily: 'JetBrains Mono',
              letterSpacing: 1,
            ),
          ),
          const SizedBox(width: 8),
          Text(
            widget.phase,
            style: TextStyle(
              color: color.withOpacity(0.6),
              fontSize: 8,
              fontWeight: FontWeight.w600,
              letterSpacing: 1,
            ),
          ),
        ],
      ),
    );
  }

  // ================================================================
  //  EVENT TICKER — scrolling bar of this tick's events
  // ================================================================

  Widget _eventTicker() {
    // Show last 3 events
    final shown = widget.events.length > 3
        ? widget.events.sublist(widget.events.length - 3)
        : widget.events;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: const Color(0xFF0A0F1E).withOpacity(0.9),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: Colors.white.withOpacity(0.06)),
      ),
      child: Row(
        children: [
          Icon(PhosphorIcons.lightning(PhosphorIconsStyle.fill),
              color: Noir.amber, size: 11),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              shown.map((e) => e.description).join('  •  '),
              style: const TextStyle(
                color: Noir.textMed,
                fontSize: 9,
                fontFamily: 'JetBrains Mono',
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
    );
  }
}

// =============================================================================
//  CITY CARD WIDGET — compact stat display
// =============================================================================

class _CityCard extends StatelessWidget {
  final City city;
  final Color color;
  final bool isUnderAttack;
  final bool isBeingBuilt;

  const _CityCard({
    required this.city,
    required this.color,
    required this.isUnderAttack,
    required this.isBeingBuilt,
  });

  @override
  Widget build(BuildContext context) {
    final borderColor = isUnderAttack
        ? Noir.rose.withOpacity(0.5)
        : isBeingBuilt
        ? Noir.emerald.withOpacity(0.4)
        : Colors.white.withOpacity(0.08);

    return Container(
      padding: const EdgeInsets.all(8),
      decoration: BoxDecoration(
        color: const Color(0xFF111827).withOpacity(0.92),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: borderColor, width: isUnderAttack ? 1.5 : 1),
        boxShadow: [
          if (isUnderAttack)
            BoxShadow(color: Noir.rose.withOpacity(0.15), blurRadius: 12),
          if (isBeingBuilt)
            BoxShadow(color: Noir.emerald.withOpacity(0.1), blurRadius: 12),
          BoxShadow(
            color: Colors.black.withOpacity(0.4),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Row 1: Name + treasury
          Row(
            children: [
              Container(
                width: 8, height: 8,
                decoration: BoxDecoration(
                  color: color,
                  shape: BoxShape.circle,
                  boxShadow: [BoxShadow(color: color.withOpacity(0.4), blurRadius: 4)],
                ),
              ),
              const SizedBox(width: 5),
              Expanded(
                child: Text(
                  city.name.toUpperCase(),
                  style: const TextStyle(
                    color: Noir.textHigh,
                    fontSize: 9,
                    fontWeight: FontWeight.w800,
                    letterSpacing: 0.8,
                  ),
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              Text(
                '\$${city.treasury.toStringAsFixed(0)}',
                style: TextStyle(
                  color: Noir.amber.withOpacity(0.8),
                  fontSize: 9,
                  fontWeight: FontWeight.w700,
                  fontFamily: 'JetBrains Mono',
                ),
              ),
            ],
          ),

          const SizedBox(height: 5),

          // Row 2: Mini stat bars
          _MiniBar(value: city.happiness,       max: 100, color: color,       label: 'HP'),
          _MiniBar(value: city.infrastructure,   max: 100, color: Noir.cyan,   label: 'INF'),
          _MiniBar(value: city.digitalDefenses,  max: 100, color: Noir.violet, label: 'DEF'),

          const SizedBox(height: 3),

          // Row 3: Population + GDP
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                _fmtPop(city.population),
                style: const TextStyle(
                  color: Noir.textLow,
                  fontSize: 8,
                  fontFamily: 'JetBrains Mono',
                ),
              ),
              Text(
                'GDP ${city.economicOutput.toStringAsFixed(1)}',
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
    );
  }

  static String _fmtPop(int pop) {
    if (pop >= 1000000) return '${(pop / 1000000).toStringAsFixed(1)}M';
    if (pop >= 1000) return '${(pop / 1000).toStringAsFixed(0)}K';
    return '$pop';
  }
}

// =============================================================================
//  MINI BAR — ultra-compact stat bar for city cards
// =============================================================================

class _MiniBar extends StatelessWidget {
  final double value;
  final double max;
  final Color color;
  final String label;

  const _MiniBar({
    required this.value,
    required this.max,
    required this.color,
    required this.label,
  });

  @override
  Widget build(BuildContext context) {
    final pct = (value / max).clamp(0.0, 1.0);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 1),
      child: Row(
        children: [
          SizedBox(
            width: 20,
            child: Text(label,
                style: TextStyle(
                    color: Noir.textLow.withOpacity(0.7),
                    fontSize: 7,
                    fontWeight: FontWeight.w600)),
          ),
          Expanded(
            child: Container(
              height: 3,
              decoration: BoxDecoration(
                color: Colors.white.withOpacity(0.05),
                borderRadius: BorderRadius.circular(2),
              ),
              child: FractionallySizedBox(
                alignment: Alignment.centerLeft,
                widthFactor: pct,
                child: Container(
                  decoration: BoxDecoration(
                    color: color.withOpacity(0.7),
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
            ),
          ),
          SizedBox(
            width: 22,
            child: Text(
              value.toStringAsFixed(0),
              textAlign: TextAlign.right,
              style: TextStyle(
                color: color.withOpacity(0.8),
                fontSize: 7,
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

// =============================================================================
//  AGENT DOT — shows which agents are at a city
// =============================================================================

class _AgentDot extends StatelessWidget {
  final String agentId;
  final bool hasEvent;

  const _AgentDot({required this.agentId, required this.hasEvent});

  @override
  Widget build(BuildContext context) {
    final color = Noir.agents[agentId] ?? Noir.textMed;
    final name = agentNames[agentId] ?? agentId;

    return Tooltip(
      message: name,
      child: Container(
        width: 10, height: 10,
        decoration: BoxDecoration(
          color: color.withOpacity(hasEvent ? 0.9 : 0.4),
          shape: BoxShape.circle,
          border: Border.all(
            color: hasEvent ? Colors.white.withOpacity(0.5) : Colors.transparent,
            width: 1,
          ),
          boxShadow: hasEvent
              ? [BoxShadow(color: color.withOpacity(0.4), blurRadius: 6)]
              : [],
        ),
      ),
    );
  }
}

// =============================================================================
//  MAP PAINTER — connections + action arcs (canvas layer)
// =============================================================================

class _MapPainter extends CustomPainter {
  final Map<String, City> cities;
  final List<EventEntry> events;
  final Offset Function(String, Size) cityPosFn;
  final Color Function(String) evtColorFn;
  final Map<String, String> agentLocations;
  final double animValue;

  _MapPainter({
    required this.cities,
    required this.events,
    required this.cityPosFn,
    required this.evtColorFn,
    required this.agentLocations,
    required this.animValue,
  });

  @override
  void paint(Canvas canvas, Size size) {
    _drawGrid(canvas, size);
    _drawRegionConnections(canvas, size);
    _drawActionArcs(canvas, size);
  }

  // Subtle grid background
  void _drawGrid(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = Colors.white.withOpacity(0.015)
      ..strokeWidth = 0.5;

    const spacing = 40.0;
    for (double x = 0; x < size.width; x += spacing) {
      canvas.drawLine(Offset(x, 0), Offset(x, size.height), paint);
    }
    for (double y = 0; y < size.height; y += spacing) {
      canvas.drawLine(Offset(0, y), Offset(size.width, y), paint);
    }
  }

  // Lines between cities in the same region
  void _drawRegionConnections(Canvas canvas, Size size) {
    // Group by region
    final groups = <String, List<String>>{};
    for (final city in cities.values) {
      final r = city.regionId.isNotEmpty ? city.regionId : 'default';
      groups.putIfAbsent(r, () => []).add(city.id);
    }

    final paint = Paint()
      ..color = Colors.white.withOpacity(0.04)
      ..strokeWidth = 1
      ..strokeCap = StrokeCap.round;

    for (final cityIds in groups.values) {
      for (int i = 0; i < cityIds.length; i++) {
        for (int j = i + 1; j < cityIds.length; j++) {
          final a = cityPosFn(cityIds[i], size);
          final b = cityPosFn(cityIds[j], size);
          canvas.drawLine(a, b, paint);
        }
      }
    }
  }

  // Animated arcs for this tick's actions
  void _drawActionArcs(Canvas canvas, Size size) {
    for (final evt in events) {
      if (evt.agent == null || evt.target == null) continue;
      if (!cities.containsKey(evt.target)) continue;

      // Source = agent's city
      final agentCity = agentLocations[evt.agent];
      if (agentCity == null || !cities.containsKey(agentCity)) continue;
      if (agentCity == evt.target) continue; // same city, skip arc

      final from = cityPosFn(agentCity, size);
      final to = cityPosFn(evt.target!, size);
      final color = evtColorFn(evt.type);

      // Curved arc
      final mid = Offset(
        (from.dx + to.dx) / 2,
        min(from.dy, to.dy) - 40,
      );

      // Draw faint arc path
      final path = Path()
        ..moveTo(from.dx, from.dy);
      path.quadraticBezierTo(mid.dx, mid.dy, to.dx, to.dy);

      canvas.drawPath(
        path,
        Paint()
          ..color = color.withOpacity(0.12)
          ..style = PaintingStyle.stroke
          ..strokeWidth = 1.5,
      );

      // Animated projectile
      final t = animValue;
      final projX = _qBez(from.dx, mid.dx, to.dx, t);
      final projY = _qBez(from.dy, mid.dy, to.dy, t);

      // Trail
      for (int j = 0; j < 4; j++) {
        final tt = (t - j * 0.04).clamp(0.0, 1.0);
        final tx = _qBez(from.dx, mid.dx, to.dx, tt);
        final ty = _qBez(from.dy, mid.dy, to.dy, tt);
        canvas.drawCircle(
          Offset(tx, ty),
          2.5 - j * 0.5,
          Paint()..color = color.withOpacity(0.4 - j * 0.08),
        );
      }

      // Projectile head
      canvas.drawCircle(
        Offset(projX, projY),
        3.5,
        Paint()..color = color.withOpacity(0.8),
      );
    }
  }

  double _qBez(double p0, double p1, double p2, double t) {
    return (1 - t) * (1 - t) * p0 + 2 * (1 - t) * t * p1 + t * t * p2;
  }

  @override
  bool shouldRepaint(covariant _MapPainter old) =>
      animValue != old.animValue ||
          events.length != old.events.length ||
          cities.length != old.cities.length;
}
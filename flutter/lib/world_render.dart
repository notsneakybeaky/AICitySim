import 'dart:math';
import 'package:flutter/material.dart';
import 'package:phosphor_flutter/phosphor_flutter.dart';
import 'models.dart';
import 'theme.dart';

class WorldRenderPanel extends StatefulWidget {
  final Map<String, City> cities;
  final List<EventEntry> events;
  final GridSnapshot grid;
  final int tick;
  final String phase, narration;
  final Map<String, String> agentLocations;
  final Map<String, int> territory; // cityId -> tile count

  const WorldRenderPanel({super.key, required this.cities, required this.events,
    required this.grid, required this.tick, required this.phase,
    this.narration = '', this.agentLocations = const {}, this.territory = const {}});

  @override
  State<WorldRenderPanel> createState() => _WorldRenderPanelState();
}

class _WorldRenderPanelState extends State<WorldRenderPanel> with SingleTickerProviderStateMixin {
  late AnimationController _anim;
  @override void initState() { super.initState(); _anim = AnimationController(vsync: this, duration: const Duration(seconds: 3))..repeat(); }
  @override void dispose() { _anim.dispose(); super.dispose(); }

  Offset _tileCenter(int tx, int ty, double tileW, double tileH) =>
      Offset((tx + 0.5) * tileW, (ty + 0.5) * tileH);

  Offset _cityCenter(String id, double tileW, double tileH) {
    final c = widget.cities[id];
    return c != null ? _tileCenter(c.tileX, c.tileY, tileW, tileH) : Offset.zero;
  }

  Color _healthColor(double h) => h >= 65 ? Noir.emerald : h >= 40 ? Noir.amber : Noir.rose;

  Color _evtColor(String t) {
    if (t.contains('ATTACK') || t.contains('SABOTAGE') || t.contains('UNREST') || t.contains('DAMAGE')) return Noir.rose;
    if (t.contains('BUILD') || t.contains('BOOST') || t.contains('DEFEND') || t.contains('INJECT')) return Noir.emerald;
    if (t.contains('BID') || t.contains('ASK') || t.contains('DRAIN') || t.contains('EARN')) return Noir.amber;
    if (t.contains('INFILTRATE') || t.contains('PROPAGANDA')) return Noir.violet;
    return Noir.cyan;
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(color: const Color(0xFF080C16), borderRadius: BorderRadius.circular(12),
          border: Border.all(color: Colors.white.withOpacity(0.05))),
      child: ClipRRect(borderRadius: BorderRadius.circular(12),
          child: widget.grid.isEmpty ? _empty() : _live()),
    );
  }

  Widget _empty() => const Center(child: Text('AWAITING WORLD DATA', style: TextStyle(color: Noir.textLow, fontSize: 11, fontWeight: FontWeight.w700, letterSpacing: 2)));

  Widget _live() {
    return LayoutBuilder(builder: (ctx, constraints) {
      final w = constraints.maxWidth;
      final h = constraints.maxHeight;
      final tileW = w / widget.grid.width;
      final tileH = h / widget.grid.height;

      return Stack(children: [
        // Layer 1: Tile grid + arrows (canvas)
        Positioned.fill(child: AnimatedBuilder(animation: _anim, builder: (_, __) =>
            CustomPaint(painter: _GridPainter(
              grid: widget.grid, cities: widget.cities, events: widget.events,
              agentLocations: widget.agentLocations, evtColorFn: _evtColor,
              tileW: tileW, tileH: tileH, animValue: _anim.value,
            )),
        )),

        // Layer 2: City labels (expanded with stats)
        ...widget.cities.entries.map((e) {
          final c = e.value;
          final pos = _tileCenter(c.tileX, c.tileY, tileW, tileH);
          final color = _healthColor(c.happiness);
          final attacked = widget.events.any((ev) => ev.target == e.key && (ev.type.contains('ATTACK') || ev.type.contains('SABOTAGE')));
          return Positioned(
            left: (pos.dx - 60).clamp(2.0, w - 122),
            top: (pos.dy - 45).clamp(2.0, h - 100),
            width: 120,
            child: _CityLabel(city: c, color: color, attacked: attacked, tiles: widget.territory[e.key] ?? 0),
          );
        }),

        // Layer 3: Agent dots below their city
        ..._agentDots(tileW, tileH),

        // HUD: tick badge top-left
        Positioned(top: 6, left: 8, child: _tickBadge()),

        // HUD: narrator bottom
        if (widget.narration.isNotEmpty)
          Positioned(bottom: 6, left: 8, right: 180, child: _narratorBar()),

        // HUD: terrain key bottom-right
        Positioned(bottom: 6, right: 6, child: _terrainKey()),
      ]);
    });
  }

  // Action icon resolver for the map
  static IconData _actionIcon(String type) {
    if (type.contains('ATTACK') || type.contains('SABOTAGE')) return PhosphorIcons.lightning(PhosphorIconsStyle.fill);
    if (type.contains('BUILD')) return PhosphorIcons.hammer(PhosphorIconsStyle.fill);
    if (type.contains('BOOST')) return PhosphorIcons.rocket(PhosphorIconsStyle.fill);
    if (type.contains('DEFEND')) return PhosphorIcons.shieldCheck(PhosphorIconsStyle.fill);
    if (type.contains('BID') || type.contains('ASK')) return PhosphorIcons.tag(PhosphorIconsStyle.fill);
    if (type.contains('DRAIN')) return PhosphorIcons.funnel(PhosphorIconsStyle.fill);
    if (type.contains('INJECT')) return PhosphorIcons.syringe(PhosphorIconsStyle.fill);
    if (type.contains('INFILTRATE')) return PhosphorIcons.eye(PhosphorIconsStyle.fill);
    if (type.contains('PROPAGANDA')) return PhosphorIcons.megaphone(PhosphorIconsStyle.fill);
    if (type.contains('ALLIANCE')) return PhosphorIcons.handshake(PhosphorIconsStyle.fill);
    if (type.contains('MOVE')) return PhosphorIcons.mapPin(PhosphorIconsStyle.fill);
    if (type.contains('UNREST')) return PhosphorIcons.fire(PhosphorIconsStyle.fill);
    return PhosphorIcons.circle(PhosphorIconsStyle.fill);
  }

  List<Widget> _agentDots(double tileW, double tileH) {
    final byCity = <String, List<String>>{};
    widget.agentLocations.forEach((a, c) => byCity.putIfAbsent(c, () => []).add(a));
    final out = <Widget>[];
    for (final e in byCity.entries) {
      final c = widget.cities[e.key];
      if (c == null) continue;
      final center = _tileCenter(c.tileX, c.tileY, tileW, tileH);
      final ids = e.value;
      final startX = center.dx - (ids.length * 18.0) / 2;
      for (int i = 0; i < ids.length; i++) {
        final agentColor = Noir.agent(ids[i]);
        final agentEvts = widget.events.where((ev) => ev.agent == ids[i]).toList();
        final active = agentEvts.isNotEmpty;
        final xPos = startX + i * 18.0;
        final yPos = center.dy + 42; // below the expanded city card

        // Action icon floating above the dot
        if (active) {
          final evtType = agentEvts.first.type;
          final icon = _actionIcon(evtType);
          final evtColor = _evtColor(evtType);
          out.add(Positioned(
            left: xPos - 3, top: yPos - 16,
            child: Container(
              padding: const EdgeInsets.all(2),
              decoration: BoxDecoration(color: evtColor.withOpacity(0.2), borderRadius: BorderRadius.circular(3),
                  border: Border.all(color: evtColor.withOpacity(0.4), width: 0.5)),
              child: Icon(icon, color: evtColor, size: 10),
            ),
          ));
        }

        // Agent dot
        out.add(Positioned(
          left: xPos, top: yPos,
          child: Tooltip(message: agentNames[ids[i]] ?? ids[i], child: Container(
            width: 10, height: 10,
            decoration: BoxDecoration(color: agentColor.withOpacity(active ? 0.9 : 0.4),
                shape: BoxShape.circle,
                border: Border.all(color: active ? Colors.white54 : Colors.transparent, width: 1),
                boxShadow: active ? [BoxShadow(color: agentColor.withOpacity(0.4), blurRadius: 5)] : []),
          )),
        ));
      }
    }
    return out;
  }

  Widget _tickBadge() {
    final live = widget.phase == 'COLLECTING' || widget.phase == 'PROCESSING';
    final c = live ? Noir.cyan : Noir.textLow;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(color: const Color(0xDD080C16), borderRadius: BorderRadius.circular(5), border: Border.all(color: c.withOpacity(0.3))),
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        if (live) Container(width: 5, height: 5, margin: const EdgeInsets.only(right: 5), decoration: BoxDecoration(color: c, shape: BoxShape.circle)),
        Text('T${widget.tick}', style: TextStyle(color: c, fontSize: 10, fontWeight: FontWeight.w800, fontFamily: 'JetBrains Mono')),
        const SizedBox(width: 6),
        Text(widget.phase, style: TextStyle(color: c.withOpacity(0.5), fontSize: 8, fontWeight: FontWeight.w600)),
      ]),
    );
  }

  Widget _narratorBar() => Container(
    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
    decoration: BoxDecoration(color: const Color(0xEE080C16), borderRadius: BorderRadius.circular(6), border: Border.all(color: Noir.primary.withOpacity(0.2))),
    child: Row(children: [
      Icon(PhosphorIcons.scroll(PhosphorIconsStyle.fill), color: Noir.primary.withOpacity(0.6), size: 12),
      const SizedBox(width: 8),
      Expanded(child: Text(widget.narration, style: const TextStyle(color: Noir.textMed, fontSize: 10, fontStyle: FontStyle.italic), maxLines: 2, overflow: TextOverflow.ellipsis)),
    ]),
  );

  Widget _terrainKey() => Container(
    padding: const EdgeInsets.all(6),
    decoration: BoxDecoration(color: const Color(0xEE080C16), borderRadius: BorderRadius.circular(6), border: Border.all(color: Colors.white.withOpacity(0.06))),
    child: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisSize: MainAxisSize.min, children: [
      const Text('TERRAIN', style: TextStyle(color: Noir.textLow, fontSize: 7, fontWeight: FontWeight.w700, letterSpacing: 1)),
      const SizedBox(height: 3),
      _keyItem(const Color(0xFF1A2B1A), 'Plains'),
      _keyItem(const Color(0xFF0F3D1F), 'Forest'),
      _keyItem(const Color(0xFF3D3D3D), 'Mountain'),
      _keyItem(const Color(0xFF0A1A3D), 'Water'),
      _keyItem(const Color(0xFF3D2D1A), 'Desert'),
      _keyItem(const Color(0xFF2A2A3D), 'Urban'),
      _keyItem(const Color(0xFF2D2A1A), 'Industrial'),
      const SizedBox(height: 4),
      const Text('OWNERSHIP', style: TextStyle(color: Noir.textLow, fontSize: 7, fontWeight: FontWeight.w700, letterSpacing: 1)),
      const SizedBox(height: 3),
      _keyItem(const Color(0xFF3B82F6), 'Nexus'),
      _keyItem(const Color(0xFFEF4444), 'Ironhold'),
      _keyItem(const Color(0xFF10B981), 'Freeport'),
      _keyItem(const Color(0xFFF59E0B), 'Eden'),
      _keyItem(const Color(0xFF8B5CF6), 'Vault'),
      _keyItem(Colors.white24, 'Unclaimed'),
    ]),
  );

  Widget _keyItem(Color c, String label) => Padding(
    padding: const EdgeInsets.only(bottom: 2),
    child: Row(mainAxisSize: MainAxisSize.min, children: [
      Container(width: 8, height: 8, decoration: BoxDecoration(color: c, borderRadius: BorderRadius.circular(2))),
      const SizedBox(width: 5),
      Text(label, style: const TextStyle(color: Noir.textLow, fontSize: 7)),
    ]),
  );
}

// =============================================================================
//  CITY LABEL — positioned at the city's tile
// =============================================================================

class _CityLabel extends StatelessWidget {
  final City city;
  final Color color;
  final bool attacked;
  final int tiles;
  const _CityLabel({required this.city, required this.color, required this.attacked, required this.tiles});

  Color _sc(double v) => v >= 65 ? Noir.emerald : v >= 40 ? Noir.amber : Noir.rose;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(5),
      decoration: BoxDecoration(
        color: const Color(0xF00D1117), borderRadius: BorderRadius.circular(6),
        border: Border.all(color: attacked ? Noir.rose.withOpacity(0.6) : color.withOpacity(0.3)),
        boxShadow: [if (attacked) BoxShadow(color: Noir.rose.withOpacity(0.2), blurRadius: 8),
          BoxShadow(color: Colors.black.withOpacity(0.6), blurRadius: 6)],
      ),
      child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
        // Name row
        Row(mainAxisSize: MainAxisSize.min, children: [
          Container(width: 6, height: 6, decoration: BoxDecoration(color: color, shape: BoxShape.circle, boxShadow: [BoxShadow(color: color.withOpacity(0.4), blurRadius: 3)])),
          const SizedBox(width: 4),
          Flexible(child: Text(city.name.toUpperCase(), overflow: TextOverflow.ellipsis,
              style: const TextStyle(color: Noir.textHigh, fontSize: 8, fontWeight: FontWeight.w800, letterSpacing: 0.5))),
          const SizedBox(width: 4),
          Text('\$${city.treasury.toStringAsFixed(0)}', style: TextStyle(color: Noir.amber.withOpacity(0.7), fontSize: 7, fontFamily: 'JetBrains Mono', fontWeight: FontWeight.w700)),
        ]),
        const SizedBox(height: 3),
        // Stat bars
        _miniStat('HP', city.happiness, _sc(city.happiness)),
        _miniStat('INF', city.infrastructure, _sc(city.infrastructure)),
        _miniStat('DEF', city.digitalDefenses, _sc(city.digitalDefenses)),
        _miniStat('COH', city.socialCohesion, _sc(city.socialCohesion)),
        _miniStat('EMP', city.employmentRate, _sc(city.employmentRate)),
        const SizedBox(height: 2),
        // Bottom: pop + territory
        Row(mainAxisSize: MainAxisSize.min, children: [
          Text(_fmtPop(city.population), style: const TextStyle(color: Noir.textLow, fontSize: 6, fontFamily: 'JetBrains Mono')),
          const SizedBox(width: 6),
          Text('${tiles}tiles', style: TextStyle(color: color.withOpacity(0.5), fontSize: 6, fontFamily: 'JetBrains Mono')),
          const SizedBox(width: 6),
          Text('GDP${city.economicOutput.toStringAsFixed(1)}', style: const TextStyle(color: Noir.textLow, fontSize: 6, fontFamily: 'JetBrains Mono')),
        ]),
      ]),
    );
  }

  Widget _miniStat(String label, double val, Color c) {
    final pct = (val / 100).clamp(0.0, 1.0);
    return Padding(padding: const EdgeInsets.only(bottom: 1), child: Row(mainAxisSize: MainAxisSize.min, children: [
      SizedBox(width: 18, child: Text(label, style: TextStyle(color: Noir.textLow.withOpacity(0.6), fontSize: 6, fontWeight: FontWeight.w600))),
      SizedBox(width: 50, height: 3, child: ClipRRect(borderRadius: BorderRadius.circular(1),
          child: LinearProgressIndicator(value: pct, minHeight: 3, backgroundColor: Colors.white.withOpacity(0.06), valueColor: AlwaysStoppedAnimation(c.withOpacity(0.7))))),
      const SizedBox(width: 3),
      SizedBox(width: 16, child: Text(val.toStringAsFixed(0), style: TextStyle(color: c.withOpacity(0.7), fontSize: 6, fontFamily: 'JetBrains Mono'))),
    ]));
  }

  static String _fmtPop(int p) => p >= 1000000 ? '${(p / 1000000).toStringAsFixed(1)}M' : p >= 1000 ? '${(p / 1000).toStringAsFixed(0)}K' : '$p';
}

// =============================================================================
//  GRID PAINTER — tiles, ownership, directional arrows
// =============================================================================

class _GridPainter extends CustomPainter {
  final GridSnapshot grid;
  final Map<String, City> cities;
  final List<EventEntry> events;
  final Map<String, String> agentLocations;
  final Color Function(String) evtColorFn;
  final double tileW, tileH, animValue;

  _GridPainter({required this.grid, required this.cities, required this.events,
    required this.agentLocations, required this.evtColorFn,
    required this.tileW, required this.tileH, required this.animValue});

  @override
  void paint(Canvas canvas, Size size) {
    _drawTiles(canvas);
    _drawArrows(canvas);
  }

  void _drawTiles(Canvas canvas) {
    final gw = grid.width;
    for (int y = 0; y < grid.height; y++) {
      for (int x = 0; x < gw; x++) {
        final idx = y * gw + x;
        if (idx >= grid.terrain.length) continue;
        final rect = Rect.fromLTWH(x * tileW, y * tileH, tileW, tileH);

        // Base terrain color
        canvas.drawRect(rect, Paint()..color = Noir.terrainColor(grid.terrain.codeUnitAt(idx)));

        // Ownership tint
        if (idx < grid.owners.length) {
          final ownerChar = grid.owners.codeUnitAt(idx);
          if (ownerChar != 46) { // not '.'
            final ownerColor = Noir.cityOwnerColors[ownerChar];
            if (ownerColor != null) {
              canvas.drawRect(rect, Paint()..color = ownerColor.withOpacity(0.15));
            }
          }
        }

        // Tile border (very subtle)
        canvas.drawRect(rect, Paint()..color = Colors.white.withOpacity(0.02)..style = PaintingStyle.stroke..strokeWidth = 0.5);
      }
    }
  }

  void _drawArrows(Canvas canvas) {
    // Collect unique directional arrows: source_city -> target, deduplicated
    final drawn = <String>{};
    for (final evt in events) {
      final src = evt.sourceCity;
      final tgt = evt.target;
      if (src == null || tgt == null || src == tgt) continue;
      if (!cities.containsKey(src) || !cities.containsKey(tgt)) continue;

      final key = '$src->$tgt';
      if (drawn.contains(key)) continue;
      drawn.add(key);

      final color = evtColorFn(evt.type);
      final from = _center(src);
      final to = _center(tgt);

      // Offset if reverse arrow exists (so lines don't overlap)
      final reverseKey = '$tgt->$src';
      final hasReverse = events.any((e) => e.sourceCity == tgt && e.target == src);
      Offset fromOff = from, toOff = to;
      if (hasReverse && drawn.contains(reverseKey)) {
        final perp = _perp(from, to, 4);
        fromOff = from + perp;
        toOff = to + perp;
      } else if (hasReverse) {
        final perp = _perp(from, to, -4);
        fromOff = from + perp;
        toOff = to + perp;
      }

      // Line
      canvas.drawLine(fromOff, toOff, Paint()..color = color.withOpacity(0.3)..strokeWidth = 2..strokeCap = StrokeCap.round);

      // Arrowhead at target
      _drawArrowhead(canvas, fromOff, toOff, color.withOpacity(0.6), 8);

      // Animated dot traveling along the arrow
      final t = animValue;
      final dx = toOff.dx - fromOff.dx, dy = toOff.dy - fromOff.dy;
      final dot = Offset(fromOff.dx + dx * t, fromOff.dy + dy * t);
      canvas.drawCircle(dot, 3, Paint()..color = color.withOpacity(0.7));
      // Trail
      for (int i = 1; i <= 3; i++) {
        final tt = (t - i * 0.04).clamp(0.0, 1.0);
        canvas.drawCircle(Offset(fromOff.dx + dx * tt, fromOff.dy + dy * tt), 2.0 - i * 0.4, Paint()..color = color.withOpacity(0.3 - i * 0.07));
      }
    }
  }

  Offset _center(String cityId) {
    final c = cities[cityId]!;
    return Offset((c.tileX + 0.5) * tileW, (c.tileY + 0.5) * tileH);
  }

  /// Perpendicular offset vector of length `d`
  Offset _perp(Offset a, Offset b, double d) {
    final dx = b.dx - a.dx, dy = b.dy - a.dy;
    final len = sqrt(dx * dx + dy * dy);
    if (len == 0) return Offset.zero;
    return Offset(-dy / len * d, dx / len * d);
  }

  void _drawArrowhead(Canvas canvas, Offset from, Offset to, Color color, double size) {
    final dx = to.dx - from.dx, dy = to.dy - from.dy;
    final len = sqrt(dx * dx + dy * dy);
    if (len == 0) return;
    final ux = dx / len, uy = dy / len;
    final tip = to;
    final left = Offset(tip.dx - ux * size - uy * size * 0.5, tip.dy - uy * size + ux * size * 0.5);
    final right = Offset(tip.dx - ux * size + uy * size * 0.5, tip.dy - uy * size - ux * size * 0.5);
    canvas.drawPath(Path()..moveTo(tip.dx, tip.dy)..lineTo(left.dx, left.dy)..lineTo(right.dx, right.dy)..close(), Paint()..color = color);
  }

  @override
  bool shouldRepaint(covariant _GridPainter old) =>
      animValue != old.animValue || events.length != old.events.length || grid.terrain != old.grid.terrain || grid.owners != old.grid.owners;
}
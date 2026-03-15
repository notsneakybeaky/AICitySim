import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:phosphor_flutter/phosphor_flutter.dart';
import 'models.dart';
import 'world_service.dart';
import 'theme.dart';
import 'world_render.dart';

// =============================================================================
//  ACTION DEF — icon + color for event types
// =============================================================================

class ActionDef {
  final IconData icon; final Color color; final String label;
  const ActionDef(this.icon, this.color, this.label);
  static final attack = ActionDef(PhosphorIcons.lightning(PhosphorIconsStyle.fill), Noir.rose, 'Attack');
  static final sabotage = ActionDef(PhosphorIcons.skull(PhosphorIconsStyle.fill), Noir.rose, 'Sabotage');
  static final unrest = ActionDef(PhosphorIcons.fire(PhosphorIconsStyle.fill), Noir.rose, 'Unrest');
  static final defend = ActionDef(PhosphorIcons.shieldCheck(PhosphorIconsStyle.fill), Noir.emerald, 'Defend');
  static final build = ActionDef(PhosphorIcons.hammer(PhosphorIconsStyle.fill), Noir.emerald, 'Build');
  static final boost = ActionDef(PhosphorIcons.rocket(PhosphorIconsStyle.fill), Noir.emerald, 'Boost');
  static final inject = ActionDef(PhosphorIcons.syringe(PhosphorIconsStyle.fill), Noir.emerald, 'Inject');
  static final bid = ActionDef(PhosphorIcons.tag(PhosphorIconsStyle.fill), Noir.amber, 'Bid');
  static final drain = ActionDef(PhosphorIcons.funnel(PhosphorIconsStyle.fill), Noir.amber, 'Drain');
  static final infiltrate = ActionDef(PhosphorIcons.eye(PhosphorIconsStyle.fill), Noir.violet, 'Infiltrate');
  static final propaganda = ActionDef(PhosphorIcons.megaphone(PhosphorIconsStyle.fill), Noir.violet, 'Propaganda');
  static final alliance = ActionDef(PhosphorIcons.handshake(PhosphorIconsStyle.fill), Noir.cyan, 'Alliance');
  static final move = ActionDef(PhosphorIcons.mapPin(PhosphorIconsStyle.fill), Noir.cyan, 'Move');
  static final unknown = ActionDef(PhosphorIcons.question(PhosphorIconsStyle.fill), Noir.textLow, '???');

  static ActionDef fromType(String t) {
    if (t.contains('ATTACK')) return attack; if (t.contains('SABOTAGE')) return sabotage;
    if (t.contains('UNREST')) return unrest; if (t.contains('DEFEND')) return defend;
    if (t.contains('BUILD')) return build; if (t.contains('BOOST')) return boost;
    if (t.contains('INJECT')) return inject; if (t.contains('BID')) return bid;
    if (t.contains('DRAIN')) return drain; if (t.contains('INFILTRATE')) return infiltrate;
    if (t.contains('PROPAGANDA')) return propaganda; if (t.contains('ALLIANCE')) return alliance;
    if (t.contains('MOVE')) return move; return unknown;
  }
}

// =============================================================================
//  DASHBOARD
// =============================================================================

class WorldDashboard extends StatefulWidget {
  const WorldDashboard({super.key});
  @override State<WorldDashboard> createState() => _WorldDashboardState();
}

class _WorldDashboardState extends State<WorldDashboard> {
  final WorldService _ws = WorldService();

  int _tick = 0;
  String _phase = 'CONNECTING', _narration = '';
  bool _connected = false;
  Map<String, City> _cities = {};
  EconomyState _economy = EconomyState();
  List<EventEntry> _events = [];
  Map<String, String> _agentLocations = {}, _agentThoughts = {};
  Map<String, int> _territory = {};
  GridSnapshot _grid = GridSnapshot(width: 25, height: 22, terrain: '', owners: '');

  @override
  void initState() {
    super.initState();
    _ws.connect().listen(_onPacket, onError: (_) {
      if (mounted) setState(() { _phase = 'DISCONNECTED'; _connected = false; });
    });
  }
  @override void dispose() { _ws.dispose(); super.dispose(); }

  void _onPacket(ServerPacket pkt) {
    if (!mounted) return;
    setState(() { try { switch (pkt.pid) {
      case S2C.handshakeAck:
        _tick = (pkt.data['current_tick'] is num) ? (pkt.data['current_tick'] as num).toInt() : _tick;
        _phase = pkt.data['state']?.toString() ?? 'SPECTATE'; _connected = true;

      case S2C.worldSnapshot:
        _tick = (pkt.data['tick'] is num) ? (pkt.data['tick'] as num).toInt() : _tick;
        _parseWorld(pkt.data['world']); _parseEconomy(pkt.data['economy']);
        _parseLoc(pkt.data['locations']); _connected = true;

      case S2C.phaseChange:
        _tick = (pkt.data['tick'] is num) ? (pkt.data['tick'] as num).toInt() : _tick;
        _phase = pkt.data['phase']?.toString() ?? _phase;

      case S2C.roundResult:
        _tick = (pkt.data['tick'] is num) ? (pkt.data['tick'] as num).toInt() : _tick;
        _parseWorld(pkt.data['world']); _parseEconomy(pkt.data['economy']);
        _parseEvents(pkt.data['events']); _parseLoc(pkt.data['locations']);
        if (pkt.data['thoughts'] is Map<String, dynamic>) _agentThoughts = (pkt.data['thoughts'] as Map<String, dynamic>).map((k, v) => MapEntry(k, v.toString()));
        _narration = pkt.data['narration']?.toString() ?? _narration;
        _phase = 'TICK_COMPLETE';

      case S2C.cityUpdate:
        final cid = pkt.data['city_id']?.toString();
        final cd = pkt.data['city'];
        if (cid != null && cd is Map<String, dynamic>) _cities[cid] = City.fromJson(cid, cd);

      case S2C.economyTick:
        _tick = (pkt.data['tick'] is num) ? (pkt.data['tick'] as num).toInt() : _tick;

      case S2C.eventLog:
        _parseEvents(pkt.data['events']);
    }} catch (e) { print('[DASH] Parse error: $e'); }});
  }

  void _parseWorld(dynamic w) {
    if (w is! Map<String, dynamic>) return;
    final cr = w['cities']; if (cr is Map<String, dynamic>) cr.forEach((id, d) { if (d is Map<String, dynamic>) _cities[id] = City.fromJson(id, d); });
    // Grid snapshot
    final g = w['grid']; if (g is Map<String, dynamic>) _grid = GridSnapshot.fromJson(g);
    // Tile deltas
    final tc = w['tile_changes']; if (tc is List && !_grid.isEmpty) _grid = _grid.applyChanges(tc);
    // Territory counts
    final t = w['territory']; if (t is Map<String, dynamic>) _territory = t.map((k, v) => MapEntry(k, (v is num) ? v.toInt() : 0));
  }
  void _parseEconomy(dynamic d) { if (d is Map<String, dynamic>) _economy = EconomyState.fromJson(d); }
  void _parseEvents(dynamic d) { if (d is List) _events = d.whereType<Map<String, dynamic>>().map((e) => EventEntry.fromJson(e)).toList(); }
  void _parseLoc(dynamic d) { if (d is Map<String, dynamic>) _agentLocations = d.map((k, v) => MapEntry(k, v.toString())); }

  // ==================================================================
  //  LAYOUT — game-like: map dominates, agents bottom, events right
  // ==================================================================

  @override
  Widget build(BuildContext context) {
    return Scaffold(backgroundColor: Noir.bg, body: Column(children: [
      _header(),
      Expanded(child: Row(children: [
        // LEFT: Map + Agent strip
        Expanded(flex: 7, child: Column(children: [
          Expanded(child: Padding(padding: const EdgeInsets.fromLTRB(8, 4, 4, 2),
              child: WorldRenderPanel(cities: _cities, events: _events, grid: _grid,
                  tick: _tick, phase: _phase, narration: _narration,
                  agentLocations: _agentLocations, territory: _territory))),
          SizedBox(height: 120, child: _agentStrip()),
        ])),
        // RIGHT: Event feed
        SizedBox(width: 320, child: _eventPanel()),
      ])),
    ]));
  }

  // ==================================================================
  //  HEADER — minimal game HUD
  // ==================================================================

  Widget _header() {
    final live = _phase == 'COLLECTING' || _phase == 'PROCESSING';
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      decoration: BoxDecoration(color: Noir.surface.withOpacity(0.4), border: Border(bottom: BorderSide(color: live ? Noir.cyan.withOpacity(0.3) : Colors.white.withOpacity(0.04)))),
      child: Row(children: [
        Container(padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3), decoration: BoxDecoration(gradient: Noir.primaryGrad, borderRadius: BorderRadius.circular(5)),
            child: const Text('H', style: TextStyle(color: Colors.white, fontSize: 14, fontWeight: FontWeight.w900))),
        const SizedBox(width: 10),
        Text('HYPERINFLATION', style: const TextStyle(color: Noir.textHigh, fontSize: 13, fontWeight: FontWeight.w800, letterSpacing: 2)),
        const SizedBox(width: 10),
        GlowDot(color: _connected ? Noir.emerald : Noir.rose, size: 5),
        const Spacer(),
        _hud('TICK', '$_tick', Noir.textHigh),
        const SizedBox(width: 16),
        _hud('PRICE', '\$${_economy.promptPrice.toStringAsFixed(2)}', Noir.emerald),
        const SizedBox(width: 16),
        _hud('DEMAND', '${_economy.currentDemand}', Noir.cyan),
        const SizedBox(width: 16),
        _hud('SUPPLY', '${_economy.totalSupply}', Noir.textMed),
        const SizedBox(width: 16),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
          decoration: BoxDecoration(color: (live ? Noir.cyan : Noir.textLow).withOpacity(0.1), borderRadius: BorderRadius.circular(12), border: Border.all(color: (live ? Noir.cyan : Noir.textLow).withOpacity(0.3))),
          child: Text(_phase, style: TextStyle(color: live ? Noir.cyan : Noir.textLow, fontSize: 9, fontWeight: FontWeight.w700, letterSpacing: 1)),
        ),
      ]),
    );
  }

  Widget _hud(String label, String value, Color c) => Column(mainAxisSize: MainAxisSize.min, children: [
    Text(label, style: const TextStyle(color: Noir.textLow, fontSize: 8, fontWeight: FontWeight.w600, letterSpacing: 1)),
    Text(value, style: TextStyle(color: c, fontSize: 14, fontWeight: FontWeight.w700, fontFamily: 'JetBrains Mono')),
  ]);

  // ==================================================================
  //  AGENT STRIP — horizontal cards at bottom
  // ==================================================================

  Widget _agentStrip() {
    if (_economy.agentEconomies.isEmpty) return const SizedBox();
    return ListView(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.fromLTRB(8, 4, 8, 6),
      children: _economy.agentEconomies.entries.map((e) => _agentCard(e.key, e.value)).toList(),
    );
  }

  Widget _agentCard(String id, AgentEconomy econ) {
    final color = Noir.agent(id);
    final name = agentNames[id] ?? id;
    final loc = _agentLocations[id];
    final thought = _agentThoughts[id];
    final lastEvents = _events.where((e) => e.agent == id).take(3).toList();

    return Container(
      width: 220, margin: const EdgeInsets.only(right: 8),
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: Colors.white.withOpacity(econ.inDebt ? 0.06 : 0.04),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: econ.inDebt ? Noir.rose.withOpacity(0.3) : color.withOpacity(0.15)),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        // Name + wallet
        Row(children: [
          Container(width: 28, height: 28, decoration: BoxDecoration(color: color.withOpacity(0.15), borderRadius: BorderRadius.circular(7), border: Border.all(color: color.withOpacity(0.3))),
              child: Center(child: Text(name[0], style: TextStyle(color: color, fontWeight: FontWeight.w800, fontSize: 13)))),
          const SizedBox(width: 8),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(name, style: TextStyle(color: color, fontWeight: FontWeight.w700, fontSize: 11)),
            if (loc != null) Text('📍 ${loc.toUpperCase()}', style: const TextStyle(color: Noir.textLow, fontSize: 8)),
          ])),
          Text('\$${econ.wallet.toStringAsFixed(0)}', style: TextStyle(color: econ.inDebt ? Noir.rose : Noir.emerald, fontWeight: FontWeight.w800, fontSize: 14, fontFamily: 'JetBrains Mono')),
        ]),
        const SizedBox(height: 6),
        // Recent actions
        if (lastEvents.isNotEmpty)
          Wrap(spacing: 4, runSpacing: 3, children: lastEvents.map((evt) {
            final def = ActionDef.fromType(evt.type);
            return Container(padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2), decoration: BoxDecoration(color: def.color.withOpacity(0.1), borderRadius: BorderRadius.circular(4)),
                child: Row(mainAxisSize: MainAxisSize.min, children: [
                  Icon(def.icon, color: def.color, size: 9), const SizedBox(width: 3),
                  Text(def.label, style: TextStyle(color: def.color, fontSize: 8, fontWeight: FontWeight.w700)),
                ]));
          }).toList())
        else if (thought != null && thought.isNotEmpty)
          Text(thought, style: TextStyle(color: Noir.textMed.withOpacity(0.7), fontSize: 9, fontStyle: FontStyle.italic), maxLines: 2, overflow: TextOverflow.ellipsis)
        else
          Text('Standing by...', style: TextStyle(color: Noir.textLow.withOpacity(0.5), fontSize: 9)),
      ]),
    );
  }

  // ==================================================================
  //  EVENT PANEL — right sidebar
  // ==================================================================

  Widget _eventPanel() {
    return Container(
      decoration: BoxDecoration(border: Border(left: BorderSide(color: Colors.white.withOpacity(0.04)))),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        // Section header
        Padding(padding: const EdgeInsets.fromLTRB(12, 10, 12, 6), child: Row(children: [
          Icon(PhosphorIcons.lightning(PhosphorIconsStyle.fill), color: Noir.amber, size: 12),
          const SizedBox(width: 6),
          Text('EVENTS • TICK $_tick', style: const TextStyle(color: Noir.textLow, fontSize: 10, fontWeight: FontWeight.w700, letterSpacing: 1.5)),
        ])),
        // Event list
        Expanded(child: _events.isEmpty
            ? Center(child: Text('Waiting for actions...', style: TextStyle(color: Noir.textMute, fontSize: 10)))
            : ListView.builder(
          padding: const EdgeInsets.symmetric(horizontal: 8),
          itemCount: _events.length,
          itemBuilder: (ctx, i) => _eventTile(_events[i]).animate().fadeIn(duration: 200.ms, delay: Duration(milliseconds: i * 30)),
        ),
        ),
      ]),
    );
  }

  Widget _eventTile(EventEntry evt) {
    final def = ActionDef.fromType(evt.type);
    final agentColor = evt.agent != null ? Noir.agent(evt.agent!) : Noir.textLow;
    return Container(
      margin: const EdgeInsets.only(bottom: 4),
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
      decoration: BoxDecoration(color: def.color.withOpacity(0.04), borderRadius: BorderRadius.circular(5),
          border: Border(left: BorderSide(color: def.color, width: 3))),
      child: Row(children: [
        Icon(def.icon, color: def.color, size: 12),
        const SizedBox(width: 6),
        if (evt.agent != null) ...[Container(width: 5, height: 5, decoration: BoxDecoration(color: agentColor, shape: BoxShape.circle)), const SizedBox(width: 5)],
        Expanded(child: Text(evt.description, style: const TextStyle(color: Noir.textMed, fontSize: 10), maxLines: 2, overflow: TextOverflow.ellipsis)),
      ]),
    );
  }
}
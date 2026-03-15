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
  List<double> _priceHistory = [];
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
  void _parseEconomy(dynamic d) {
    if (d is Map<String, dynamic>) {
      _economy = EconomyState.fromJson(d);
      _priceHistory.add(_economy.promptPrice);
      if (_priceHistory.length > 100) _priceHistory = _priceHistory.sublist(_priceHistory.length - 100);
    }
  }
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
          SizedBox(height: 140, child: _agentStrip()),
        ])),
        // RIGHT: Leaderboard + Chart + AI Thoughts + Events
        SizedBox(width: 380, child: _rightPanel()),
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
  //  AGENT STRIP — horizontal cards showing AI thinking
  // ==================================================================

  Widget _agentStrip() {
    if (_economy.agentEconomies.isEmpty) return const SizedBox();
    // Sort by wallet descending — show who's winning
    final sorted = _economy.agentEconomies.entries.toList()
      ..sort((a, b) => b.value.wallet.compareTo(a.value.wallet));
    return ListView(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.fromLTRB(8, 4, 8, 6),
      children: [
        for (int i = 0; i < sorted.length; i++)
          _agentCard(sorted[i].key, sorted[i].value, rank: i + 1),
      ],
    );
  }

  Widget _agentCard(String id, AgentEconomy econ, {required int rank}) {
    final color = Noir.agent(id);
    final name = agentNames[id] ?? id;
    final loc = _agentLocations[id];
    final thought = _agentThoughts[id];
    final lastEvents = _events.where((e) => e.agent == id).take(3).toList();
    final rankColor = rank == 1 ? Noir.amber : rank == 2 ? Noir.textMed : Noir.textLow;

    return Container(
      width: 250, margin: const EdgeInsets.only(right: 8),
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: Colors.white.withOpacity(econ.inDebt ? 0.06 : 0.04),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: econ.inDebt ? Noir.rose.withOpacity(0.3) : color.withOpacity(0.15)),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        // Name + rank + wallet
        Row(children: [
          // Rank badge
          Container(width: 18, height: 18, decoration: BoxDecoration(color: rankColor.withOpacity(0.15), borderRadius: BorderRadius.circular(4)),
              child: Center(child: Text('#$rank', style: TextStyle(color: rankColor, fontSize: 9, fontWeight: FontWeight.w800)))),
          const SizedBox(width: 6),
          Container(width: 24, height: 24, decoration: BoxDecoration(color: color.withOpacity(0.15), borderRadius: BorderRadius.circular(6), border: Border.all(color: color.withOpacity(0.3))),
              child: Center(child: Text(name[0], style: TextStyle(color: color, fontWeight: FontWeight.w800, fontSize: 12)))),
          const SizedBox(width: 6),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(name, style: TextStyle(color: color, fontWeight: FontWeight.w700, fontSize: 11)),
            if (loc != null) Text(loc.toUpperCase(), style: const TextStyle(color: Noir.textLow, fontSize: 7, letterSpacing: 0.5)),
          ])),
          Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
            Text('\$${econ.wallet.toStringAsFixed(0)}', style: TextStyle(color: econ.inDebt ? Noir.rose : Noir.emerald, fontWeight: FontWeight.w800, fontSize: 14, fontFamily: 'JetBrains Mono')),
            if (econ.inDebt) Text('DEBT', style: TextStyle(color: Noir.rose, fontSize: 7, fontWeight: FontWeight.w800)),
          ]),
        ]),
        const SizedBox(height: 6),
        // Recent action chips
        if (lastEvents.isNotEmpty)
          Wrap(spacing: 4, runSpacing: 3, children: lastEvents.map((evt) {
            final def = ActionDef.fromType(evt.type);
            return Container(padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2), decoration: BoxDecoration(color: def.color.withOpacity(0.1), borderRadius: BorderRadius.circular(4)),
                child: Row(mainAxisSize: MainAxisSize.min, children: [
                  Icon(def.icon, color: def.color, size: 9), const SizedBox(width: 3),
                  Text(def.label, style: TextStyle(color: def.color, fontSize: 8, fontWeight: FontWeight.w700)),
                  if (evt.target != null) ...[const SizedBox(width: 2), Text('→${evt.target}', style: TextStyle(color: def.color.withOpacity(0.5), fontSize: 7))],
                ]));
          }).toList()),
        const SizedBox(height: 4),
        // AI THOUGHT — always visible, this is the key thing judges see
        if (thought != null && thought.isNotEmpty)
          Container(
            padding: const EdgeInsets.all(6),
            decoration: BoxDecoration(color: color.withOpacity(0.06), borderRadius: BorderRadius.circular(5), border: Border.all(color: color.withOpacity(0.1))),
            child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Icon(PhosphorIcons.brain(PhosphorIconsStyle.fill), color: color.withOpacity(0.5), size: 10),
              const SizedBox(width: 5),
              Expanded(child: Text('"$thought"', style: TextStyle(color: Noir.textMed, fontSize: 9, fontStyle: FontStyle.italic, height: 1.3), maxLines: 2, overflow: TextOverflow.ellipsis)),
            ]),
          )
        else
          Text('Thinking...', style: TextStyle(color: Noir.textLow.withOpacity(0.4), fontSize: 9, fontStyle: FontStyle.italic)),
      ]),
    );
  }

  // ==================================================================
  //  RIGHT PANEL — Leaderboard + Price Chart + Events
  // ==================================================================

  Widget _rightPanel() {
    return Container(
      decoration: BoxDecoration(border: Border(left: BorderSide(color: Colors.white.withOpacity(0.04)))),
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(10),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          // ---- WALLET LEADERBOARD ----
          _sectionHeader('LEADERBOARD', PhosphorIcons.trophy(PhosphorIconsStyle.fill), Noir.amber),
          const SizedBox(height: 6),
          _leaderboard(),
          const SizedBox(height: 14),

          // ---- PROMPT PRICE CHART ----
          if (_priceHistory.length >= 2) ...[
            _sectionHeader('PROMPT PRICE', PhosphorIcons.chartLineUp(PhosphorIconsStyle.fill), Noir.emerald),
            const SizedBox(height: 6),
            Container(
              height: 100,
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(color: Colors.white.withOpacity(0.03), borderRadius: BorderRadius.circular(8)),
              child: CustomPaint(size: const Size(double.infinity, 84), painter: _PriceChartPainter(_priceHistory)),
            ),
            const SizedBox(height: 14),
          ],

          // ---- MARKET STATS ----
          _sectionHeader('MARKET', PhosphorIcons.storefront(PhosphorIconsStyle.fill), Noir.cyan),
          const SizedBox(height: 6),
          _marketRow('Price', '\$${_economy.promptPrice.toStringAsFixed(2)}', Noir.emerald),
          _marketRow('Demand', '${_economy.currentDemand}', Noir.cyan),
          _marketRow('Supply', '${_economy.totalSupply}', Noir.textMed),
          _marketRow('Open Orders', '${_economy.openOrders}', Noir.textLow),
          const SizedBox(height: 14),

          // ---- EVENTS ----
          _sectionHeader('EVENTS • TICK $_tick', PhosphorIcons.lightning(PhosphorIconsStyle.fill), Noir.amber),
          const SizedBox(height: 6),
          if (_events.isEmpty)
            Padding(padding: const EdgeInsets.symmetric(vertical: 12), child: Text('Waiting for actions...', style: TextStyle(color: Noir.textMute, fontSize: 10)))
          else
            ..._events.map((evt) => _eventTile(evt)),
        ]),
      ),
    );
  }

  Widget _leaderboard() {
    if (_economy.agentEconomies.isEmpty) return Text('No data yet', style: TextStyle(color: Noir.textMute, fontSize: 10));
    final sorted = _economy.agentEconomies.entries.toList()
      ..sort((a, b) => b.value.wallet.compareTo(a.value.wallet));
    final topWallet = sorted.first.value.wallet.abs().clamp(1.0, double.infinity);

    return Column(children: [
      for (int i = 0; i < sorted.length; i++) ...[
        _leaderboardRow(sorted[i].key, sorted[i].value, i + 1, topWallet),
        if (i < sorted.length - 1) const SizedBox(height: 3),
      ],
    ]);
  }

  Widget _leaderboardRow(String id, AgentEconomy econ, int rank, double topWallet) {
    final color = Noir.agent(id);
    final name = agentNames[id] ?? id;
    final barWidth = (econ.wallet.abs() / topWallet).clamp(0.0, 1.0);
    final isFirst = rank == 1;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
      decoration: BoxDecoration(
        color: isFirst ? color.withOpacity(0.08) : Colors.white.withOpacity(0.02),
        borderRadius: BorderRadius.circular(6),
        border: isFirst ? Border.all(color: color.withOpacity(0.2)) : null,
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Text('#$rank', style: TextStyle(color: isFirst ? Noir.amber : Noir.textLow, fontSize: 10, fontWeight: FontWeight.w800, fontFamily: 'JetBrains Mono')),
          const SizedBox(width: 6),
          Container(width: 6, height: 6, decoration: BoxDecoration(color: color, shape: BoxShape.circle)),
          const SizedBox(width: 5),
          Expanded(child: Text(name, style: TextStyle(color: color, fontSize: 10, fontWeight: FontWeight.w700))),
          Text('\$${econ.wallet.toStringAsFixed(0)}', style: TextStyle(color: econ.inDebt ? Noir.rose : Noir.emerald, fontSize: 11, fontWeight: FontWeight.w800, fontFamily: 'JetBrains Mono')),
        ]),
        const SizedBox(height: 3),
        // Wallet bar
        ClipRRect(borderRadius: BorderRadius.circular(2), child: LinearProgressIndicator(
          value: barWidth, minHeight: 3, backgroundColor: Colors.white.withOpacity(0.04),
          valueColor: AlwaysStoppedAnimation(econ.inDebt ? Noir.rose.withOpacity(0.5) : color.withOpacity(0.5)),
        )),
      ]),
    );
  }

  Widget _marketRow(String label, String value, Color c) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 2),
    child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
      Text(label, style: const TextStyle(color: Noir.textMed, fontSize: 10)),
      Text(value, style: TextStyle(color: c, fontSize: 11, fontWeight: FontWeight.w700, fontFamily: 'JetBrains Mono')),
    ]),
  );

  Widget _sectionHeader(String title, IconData icon, Color color) => Row(children: [
    Icon(icon, color: color.withOpacity(0.7), size: 12),
    const SizedBox(width: 6),
    Text(title, style: TextStyle(color: color.withOpacity(0.8), fontSize: 10, fontWeight: FontWeight.w700, letterSpacing: 1.5)),
  ]);

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

// ==================================================================
//  PRICE CHART PAINTER
// ==================================================================

class _PriceChartPainter extends CustomPainter {
  final List<double> prices;
  _PriceChartPainter(this.prices);

  @override
  void paint(Canvas canvas, Size size) {
    if (prices.length < 2) return;
    final minP = prices.reduce((a, b) => a < b ? a : b) * 0.95;
    final maxP = prices.reduce((a, b) => a > b ? a : b) * 1.05;
    final range = maxP - minP;
    if (range == 0) return;

    final isUp = prices.last >= prices.first;
    final lineColor = isUp ? Noir.emerald : Noir.rose;

    // Grid lines
    final gridPaint = Paint()..color = Colors.white.withOpacity(0.04)..strokeWidth = 0.5;
    for (int i = 0; i < 3; i++) canvas.drawLine(Offset(0, size.height * i / 2), Offset(size.width, size.height * i / 2), gridPaint);

    // Fill
    final fillPath = Path();
    final linePath = Path();
    for (int i = 0; i < prices.length; i++) {
      final x = (i / (prices.length - 1)) * size.width;
      final y = size.height - ((prices[i] - minP) / range) * size.height;
      if (i == 0) { linePath.moveTo(x, y); fillPath.moveTo(x, size.height); fillPath.lineTo(x, y); }
      else { linePath.lineTo(x, y); fillPath.lineTo(x, y); }
    }
    fillPath.lineTo(size.width, size.height); fillPath.close();
    canvas.drawPath(fillPath, Paint()..shader = LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter,
        colors: [lineColor.withOpacity(0.2), lineColor.withOpacity(0.0)]).createShader(Rect.fromLTWH(0, 0, size.width, size.height)));
    canvas.drawPath(linePath, Paint()..color = lineColor..strokeWidth = 1.5..style = PaintingStyle.stroke..strokeCap = StrokeCap.round);

    // Dot + label at end
    final lastY = size.height - ((prices.last - minP) / range) * size.height;
    canvas.drawCircle(Offset(size.width, lastY), 3, Paint()..color = lineColor);
    final tp = TextPainter(text: TextSpan(text: '\$${prices.last.toStringAsFixed(2)}', style: TextStyle(color: lineColor, fontSize: 9, fontWeight: FontWeight.w700)), textDirection: TextDirection.ltr);
    tp.layout(); tp.paint(canvas, Offset(size.width - tp.width - 4, lastY - 14));
  }

  @override bool shouldRepaint(covariant _PriceChartPainter old) => prices.length != old.prices.length;
}
import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:phosphor_flutter/phosphor_flutter.dart';
import 'models.dart';
import 'world_service.dart';
import 'theme.dart';
import 'world_render.dart';

class WorldDashboard extends StatefulWidget {
  const WorldDashboard({super.key});

  @override
  State<WorldDashboard> createState() => _WorldDashboardState();
}

class _WorldDashboardState extends State<WorldDashboard> {
  final WorldService _ws = WorldService();

  int _tick = 0;
  String _phase = 'CONNECTING';
  String? _clientId;
  List<String> _activeModules = [];
  Map<String, City> _cities = {};
  EconomyState _economy = EconomyState();
  List<EventEntry> _events = [];
  List<double> _priceHistory = [];
  bool _connected = false;

  @override
  void initState() {
    super.initState();
    _ws.connect().listen(_onPacket, onError: (_) {
      setState(() {
        _phase = 'DISCONNECTED';
        _connected = false;
      });
    });
  }

  @override
  void dispose() {
    _ws.dispose();
    super.dispose();
  }

  void _onPacket(ServerPacket pkt) {
    setState(() {
      switch (pkt.pid) {
        case S2C.handshakeAck:
          _clientId = pkt.data['client_id'];
          _tick = pkt.data['current_tick'] ?? 0;
          _activeModules =
          List<String>.from(pkt.data['active_modules'] ?? []);
          _phase = pkt.data['state'] ?? 'SPECTATE';
          _connected = true;

        case S2C.worldSnapshot:
          _tick = pkt.data['tick'] ?? _tick;
          _parseCities(pkt.data['world']);
          if (pkt.data['economy'] != null) {
            _economy = EconomyState.fromJson(pkt.data['economy']);
          }

        case S2C.phaseChange:
          _tick = pkt.data['tick'] ?? _tick;
          _phase = pkt.data['phase'] ?? _phase;

        case S2C.roundResult:
          _tick = pkt.data['tick'] ?? _tick;
          _parseCities(pkt.data['world']);
          if (pkt.data['economy'] != null) {
            _economy = EconomyState.fromJson(pkt.data['economy']);
            _priceHistory.add(_economy.promptPrice);
            if (_priceHistory.length > 100) {
              _priceHistory =
                  _priceHistory.sublist(_priceHistory.length - 100);
            }
          }
          final rawEvents = pkt.data['events'] as List<dynamic>? ?? [];
          _events = rawEvents
              .map((e) => EventEntry.fromJson(e as Map<String, dynamic>))
              .toList();
          _phase = 'TICK_COMPLETE';

        case S2C.cityUpdate:
          final cityId = pkt.data['city_id'] as String?;
          final cityData = pkt.data['city'] as Map<String, dynamic>?;
          if (cityId != null && cityData != null) {
            _cities[cityId] = City.fromJson(cityId, cityData);
          }

        case S2C.economyTick:
          _tick = pkt.data['tick'] ?? _tick;

        case S2C.eventLog:
          final rawEvts = pkt.data['events'] as List<dynamic>? ?? [];
          _events = rawEvts
              .map((e) => EventEntry.fromJson(e as Map<String, dynamic>))
              .toList();
      }
    });
  }

  void _parseCities(Map<String, dynamic>? worldData) {
    if (worldData == null) return;
    final citiesRaw = worldData['cities'] as Map<String, dynamic>? ?? {};
    citiesRaw.forEach((id, data) {
      if (data is Map<String, dynamic>) {
        _cities[id] = City.fromJson(id, data);
      }
    });
  }

  // ==================================================================
  //  BUILD — new 3-zone layout: render + cities | side panel
  // ==================================================================

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Noir.bg,
      body: Column(
        children: [
          _buildHeader(),
          Expanded(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // LEFT COLUMN: Render on top, cities on bottom
                Expanded(
                  flex: 3,
                  child: Column(
                    children: [
                      // World render area
                      Expanded(
                        flex: 5,
                        child: WorldRenderPanel(
                          cities: _cities,
                          events: _events,
                          tick: _tick,
                          phase: _phase,
                        ),
                      ),
                      // City cards
                      Expanded(
                        flex: 4,
                        child: _buildCityPanel(),
                      ),
                    ],
                  ),
                ),
                Container(
                  width: 1,
                  color: Noir.textMute.withOpacity(0.2),
                ),
                // RIGHT COLUMN: Economy + agents + events
                Expanded(flex: 2, child: _buildSidePanel()),
              ],
            ),
          ),
        ],
      ),
    );
  }

  // ==================================================================
  //  HEADER
  // ==================================================================

  Widget _buildHeader() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 16),
      decoration: BoxDecoration(
        color: Noir.surface.withOpacity(0.6),
        border: Border(
          bottom: BorderSide(color: _phaseColor().withOpacity(0.4), width: 1),
        ),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
            decoration: BoxDecoration(
              gradient: Noir.primaryGrad,
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Text(
              'H',
              style: TextStyle(
                  color: Colors.white,
                  fontSize: 18,
                  fontWeight: FontWeight.w900),
            ),
          ),
          const SizedBox(width: 14),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text(
                'HYPERINFLATION',
                style: TextStyle(
                  color: Noir.textHigh,
                  fontSize: 15,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 3,
                ),
              ),
              const SizedBox(height: 2),
              Row(
                children: [
                  GlowDot(
                      color: _connected ? Noir.emerald : Noir.rose, size: 6),
                  const SizedBox(width: 6),
                  Text(
                    _connected
                        ? '${_clientId ?? 'connected'}'
                        : 'disconnected',
                    style: TextStyle(
                        color: _connected ? Noir.textLow : Noir.rose,
                        fontSize: 10),
                  ),
                ],
              ),
            ],
          ),
          const Spacer(),
          _phasePill(),
          const SizedBox(width: 24),
          _headerMetric(
            PhosphorIcons.timer(PhosphorIconsStyle.bold),
            'TICK',
            '$_tick',
          ),
          const SizedBox(width: 20),
          _headerMetric(
            PhosphorIcons.currencyDollar(PhosphorIconsStyle.bold),
            'PROMPT',
            _economy.promptPrice.toStringAsFixed(2),
            valueColor: Noir.emerald,
            large: true,
          ),
          const SizedBox(width: 20),
          _headerMetric(
            PhosphorIcons.trendUp(PhosphorIconsStyle.bold),
            'DEMAND',
            '${_economy.currentDemand}',
            valueColor: Noir.cyan,
          ),
          const SizedBox(width: 20),
          _headerMetric(
            PhosphorIcons.package(PhosphorIconsStyle.bold),
            'SUPPLY',
            '${_economy.totalSupply}',
          ),
        ],
      ),
    );
  }

  Widget _phasePill() {
    final color = _phaseColor();
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: color.withOpacity(0.3), width: 1),
        boxShadow: [
          BoxShadow(color: color.withOpacity(0.1), blurRadius: 12),
        ],
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          GlowDot(color: color, size: 6),
          const SizedBox(width: 8),
          Text(
            _phase,
            style: TextStyle(
              color: color,
              fontWeight: FontWeight.w700,
              fontSize: 11,
              letterSpacing: 1.5,
            ),
          ),
        ],
      ),
    );
  }

  Widget _headerMetric(IconData icon, String label, String value,
      {Color valueColor = Noir.textHigh, bool large = false}) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, color: Noir.textLow, size: 14),
        const SizedBox(width: 8),
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(label,
                style: const TextStyle(
                    color: Noir.textLow,
                    fontSize: 9,
                    fontWeight: FontWeight.w600,
                    letterSpacing: 1.5)),
            Text(value,
                style: TextStyle(
                    color: valueColor,
                    fontSize: large ? 20 : 15,
                    fontWeight: FontWeight.w700,
                    fontFamily: 'JetBrains Mono')),
          ],
        ),
      ],
    );
  }

  Color _phaseColor() {
    switch (_phase) {
      case 'COLLECTING':
        return Noir.cyan;
      case 'PROCESSING':
        return Noir.amber;
      case 'BROADCASTING':
        return Noir.violet;
      case 'TICK_COMPLETE':
        return Noir.emerald;
      case 'DISCONNECTED':
        return Noir.rose;
      default:
        return Noir.textLow;
    }
  }

  // ==================================================================
  //  CITY PANEL (now below render)
  // ==================================================================

  Widget _buildCityPanel() {
    if (_cities.isEmpty) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(PhosphorIcons.buildings(PhosphorIconsStyle.duotone),
                color: Noir.textMute, size: 40),
            const SizedBox(height: 10),
            const Text('Waiting for world data...',
                style: TextStyle(color: Noir.textLow, fontSize: 12)),
          ],
        )
            .animate(onPlay: (c) => c.repeat(reverse: true))
            .fadeIn(duration: 600.ms)
            .then()
            .shimmer(
            duration: 1200.ms, color: Noir.primary.withOpacity(0.08)),
      );
    }

    final cities = _cities.values.toList();

    // Horizontal scrolling city cards for compact layout
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 6),
          child: Row(
            children: [
              Icon(PhosphorIcons.buildings(PhosphorIconsStyle.fill),
                  color: Noir.textLow, size: 12),
              const SizedBox(width: 6),
              const Text(
                'CITIES',
                style: TextStyle(
                  color: Noir.textLow,
                  fontSize: 10,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 2,
                ),
              ),
              const SizedBox(width: 8),
              Text(
                '${cities.length} active',
                style: const TextStyle(color: Noir.textMute, fontSize: 9),
              ),
            ],
          ),
        ),
        Expanded(
          child: ListView.builder(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
            itemCount: cities.length,
            itemBuilder: (context, i) {
              return _buildCompactCityCard(cities[i])
                  .animate()
                  .fadeIn(
                  duration: 350.ms,
                  delay: Duration(milliseconds: i * 60))
                  .slideX(begin: 0.05, end: 0, curve: Curves.easeOut);
            },
          ),
        ),
      ],
    );
  }

  Widget _buildCompactCityCard(City city) {
    final isStruggling = city.happiness < 40;
    final borderColor = isStruggling
        ? Noir.rose.withOpacity(0.25)
        : Colors.white.withOpacity(0.06);

    return Container(
      width: 260,
      margin: const EdgeInsets.only(right: 10),
      child: GlassCard(
        padding: const EdgeInsets.all(14),
        borderColor: borderColor,
        opacity: isStruggling ? 0.08 : 0.05,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Header
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(6),
                  decoration: BoxDecoration(
                    color: Noir.primary.withOpacity(0.15),
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Icon(
                    PhosphorIcons.buildings(PhosphorIconsStyle.fill),
                    color: Noir.primary,
                    size: 14,
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        city.name.toUpperCase(),
                        style: const TextStyle(
                          color: Noir.textHigh,
                          fontWeight: FontWeight.w700,
                          fontSize: 12,
                          letterSpacing: 1,
                        ),
                      ),
                      Text(
                        '${city.regionId.toUpperCase()}  •  ${_formatPop(city.population)}',
                        style: const TextStyle(
                            color: Noir.textLow, fontSize: 9),
                      ),
                    ],
                  ),
                ),
                if (isStruggling)
                  Icon(PhosphorIcons.warning(PhosphorIconsStyle.fill),
                      color: Noir.rose, size: 14),
              ],
            ),

            const SizedBox(height: 12),

            // Stats
            StatBar(
                label: 'Happiness',
                value: city.happiness,
                color: _statColor(city.happiness)),
            StatBar(
                label: 'Infra',
                value: city.infrastructure,
                color: _statColor(city.infrastructure)),
            StatBar(
                label: 'Defenses',
                value: city.digitalDefenses,
                color: _statColor(city.digitalDefenses)),
            StatBar(
                label: 'Cohesion',
                value: city.socialCohesion,
                color: _statColor(city.socialCohesion)),
            StatBar(
                label: 'Employment',
                value: city.employmentRate,
                color: _statColor(city.employmentRate)),

            const Spacer(),

            // Bottom metrics
            Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: Noir.surface.withOpacity(0.5),
                borderRadius: BorderRadius.circular(6),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  _tinyMetric('\$${city.treasury.toStringAsFixed(0)}',
                      'Treasury', Noir.amber),
                  _tinyMetric(city.economicOutput.toStringAsFixed(2),
                      'GDP', Noir.emerald),
                  _tinyMetric(
                      '${(city.taxRate * 100).toStringAsFixed(0)}%',
                      'Tax',
                      Noir.textMed),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _tinyMetric(String value, String label, Color color) {
    return Column(
      children: [
        Text(value,
            style: TextStyle(
                color: color,
                fontSize: 12,
                fontWeight: FontWeight.w700,
                fontFamily: 'JetBrains Mono')),
        Text(label,
            style: const TextStyle(color: Noir.textLow, fontSize: 8)),
      ],
    );
  }

  Color _statColor(double v) {
    if (v >= 70) return Noir.emerald;
    if (v >= 40) return Noir.amber;
    return Noir.rose;
  }

  String _formatPop(int pop) {
    if (pop >= 1000000) return '${(pop / 1000000).toStringAsFixed(1)}M';
    if (pop >= 1000) return '${(pop / 1000).toStringAsFixed(0)}K';
    return '$pop';
  }

  // ==================================================================
  //  SIDE PANEL (RIGHT) — unchanged logic, same as before
  // ==================================================================

  Widget _buildSidePanel() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _section('PROMPT MARKET',
              PhosphorIcons.storefront(PhosphorIconsStyle.fill)),
          const SizedBox(height: 10),
          GlassCard(
            padding: const EdgeInsets.all(14),
            child: Column(
              children: [
                _metricRow(
                    'Price',
                    '\$${_economy.promptPrice.toStringAsFixed(2)}',
                    Noir.emerald),
                _metricRow(
                    'Demand', '${_economy.currentDemand}', Noir.cyan),
                _metricRow(
                    'Supply', '${_economy.totalSupply}', Noir.textMed),
                _metricRow(
                    'Market Value',
                    '\$${_economy.totalMarketValue.toStringAsFixed(0)}',
                    Noir.amber),
                _metricRow(
                    'Open Orders', '${_economy.openOrders}', Noir.textLow),
              ],
            ),
          ),
          const SizedBox(height: 16),
          if (_priceHistory.length >= 2) ...[
            _section('PRICE HISTORY',
                PhosphorIcons.chartLineUp(PhosphorIconsStyle.fill)),
            const SizedBox(height: 10),
            GlassCard(
              padding: const EdgeInsets.fromLTRB(12, 16, 12, 12),
              child: SizedBox(
                height: 110,
                child: CustomPaint(
                  size: const Size(double.infinity, 110),
                  painter: PriceChartPainter(_priceHistory),
                ),
              ),
            ),
            const SizedBox(height: 16),
          ],
          _section(
              'AGENTS', PhosphorIcons.users(PhosphorIconsStyle.fill)),
          const SizedBox(height: 10),
          ..._economy.agentEconomies.entries
              .toList()
              .asMap()
              .entries
              .map((entry) {
            final i = entry.key;
            final id = entry.value.key;
            final econ = entry.value.value;
            return _buildAgentCard(id, econ)
                .animate()
                .fadeIn(
                duration: 300.ms,
                delay: Duration(milliseconds: i * 60))
                .slideX(begin: 0.02, end: 0);
          }),
          const SizedBox(height: 16),
          _section('EVENTS • TICK $_tick',
              PhosphorIcons.lightning(PhosphorIconsStyle.fill)),
          const SizedBox(height: 10),
          if (_events.isEmpty)
            const Text('No events yet.',
                style: TextStyle(color: Noir.textLow, fontSize: 12))
          else
            ..._events.asMap().entries.map((entry) {
              final i = entry.key;
              final evt = entry.value;
              return _buildEventTile(evt)
                  .animate()
                  .fadeIn(
                  duration: 200.ms,
                  delay: Duration(milliseconds: i * 40))
                  .slideY(begin: 0.05, end: 0);
            }),
        ],
      ),
    );
  }

  Widget _section(String title, IconData icon) {
    return Row(
      children: [
        Icon(icon, color: Noir.textLow, size: 13),
        const SizedBox(width: 8),
        Text(title,
            style: const TextStyle(
                color: Noir.textLow,
                fontSize: 11,
                fontWeight: FontWeight.w700,
                letterSpacing: 2)),
      ],
    );
  }

  Widget _metricRow(String label, String value, Color color) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label,
              style: const TextStyle(color: Noir.textMed, fontSize: 12)),
          Text(value,
              style: TextStyle(
                  color: color,
                  fontSize: 14,
                  fontWeight: FontWeight.w700,
                  fontFamily: 'JetBrains Mono')),
        ],
      ),
    );
  }

  Widget _buildAgentCard(String id, AgentEconomy econ) {
    final color = Noir.agent(id);
    final name = agentNames[id] ?? id;

    return GlassCard(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(12),
      opacity: econ.inDebt ? 0.08 : 0.05,
      borderColor: econ.inDebt ? Noir.rose.withOpacity(0.25) : null,
      child: Row(
        children: [
          Container(
            width: 36,
            height: 36,
            decoration: BoxDecoration(
              color: color.withOpacity(0.15),
              borderRadius: BorderRadius.circular(10),
              border: Border.all(color: color.withOpacity(0.3)),
            ),
            child: Center(
                child: Text(name[0],
                    style: TextStyle(
                        color: color,
                        fontWeight: FontWeight.w800,
                        fontSize: 16))),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(name,
                    style: TextStyle(
                        color: color,
                        fontWeight: FontWeight.w700,
                        fontSize: 12)),
                const SizedBox(height: 2),
                Text(
                  'Alloc: ${econ.allocatedPrompts}  •  Served: ${econ.promptsServed}',
                  style: const TextStyle(color: Noir.textLow, fontSize: 10),
                ),
              ],
            ),
          ),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text('\$${econ.wallet.toStringAsFixed(1)}',
                  style: TextStyle(
                      color: econ.inDebt ? Noir.rose : Noir.emerald,
                      fontWeight: FontWeight.w800,
                      fontSize: 15,
                      fontFamily: 'JetBrains Mono')),
              if (econ.inDebt)
                Container(
                  margin: const EdgeInsets.only(top: 2),
                  padding:
                  const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
                  decoration: BoxDecoration(
                    color: Noir.rose.withOpacity(0.2),
                    borderRadius: BorderRadius.circular(4),
                  ),
                  child: const Text('DEBT',
                      style: TextStyle(
                          color: Noir.rose,
                          fontSize: 8,
                          fontWeight: FontWeight.w800,
                          letterSpacing: 1)),
                ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildEventTile(EventEntry evt) {
    final color = _eventColor(evt.type);
    final agentColor =
    evt.agent != null ? Noir.agent(evt.agent!) : Noir.textLow;

    return Container(
      margin: const EdgeInsets.only(bottom: 5),
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 7),
      decoration: BoxDecoration(
        color: color.withOpacity(0.05),
        borderRadius: BorderRadius.circular(6),
        border: Border(left: BorderSide(color: color, width: 3)),
      ),
      child: Row(
        children: [
          if (evt.agent != null) ...[
            Container(
                width: 6,
                height: 6,
                decoration:
                BoxDecoration(color: agentColor, shape: BoxShape.circle)),
            const SizedBox(width: 8),
          ],
          Expanded(
            child: Text(evt.description,
                style: const TextStyle(color: Noir.textMed, fontSize: 11),
                maxLines: 2,
                overflow: TextOverflow.ellipsis),
          ),
          const SizedBox(width: 8),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
            decoration: BoxDecoration(
              color: color.withOpacity(0.1),
              borderRadius: BorderRadius.circular(4),
            ),
            child: Text(evt.type,
                style: TextStyle(
                    color: color,
                    fontSize: 9,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 0.5)),
          ),
        ],
      ),
    );
  }

  Color _eventColor(String type) {
    if (type.contains('ATTACK') || type.contains('SABOTAGE') ||
        type.contains('UNREST')) {
      return Noir.rose;
    }
    if (type.contains('BUILD') || type.contains('BOOST') ||
        type.contains('DEFEND') || type.contains('INJECT')) {
      return Noir.emerald;
    }
    if (type.contains('BID') || type.contains('DRAIN') ||
        type.contains('EARN')) {
      return Noir.amber;
    }
    if (type.contains('INFILTRATE') || type.contains('PROPAGANDA')) {
      return Noir.violet;
    }
    if (type.contains('ALLIANCE')) return Noir.cyan;
    return Noir.textLow;
  }
}

// ==================================================================
//  PRICE CHART (extracted so render file can exist cleanly)
// ==================================================================

class PriceChartPainter extends CustomPainter {
  final List<double> prices;
  PriceChartPainter(this.prices);

  @override
  void paint(Canvas canvas, Size size) {
    if (prices.length < 2) return;

    final minP = prices.reduce((a, b) => a < b ? a : b) * 0.95;
    final maxP = prices.reduce((a, b) => a > b ? a : b) * 1.05;
    final range = maxP - minP;
    if (range == 0) return;

    final gridPaint = Paint()
      ..color = Noir.textMute.withOpacity(0.2)
      ..strokeWidth = 0.5;
    for (int i = 0; i < 4; i++) {
      final y = size.height * (i / 3);
      canvas.drawLine(Offset(0, y), Offset(size.width, y), gridPaint);
    }

    final isUp = prices.last >= prices.first;
    final lineColor = isUp ? Noir.emerald : Noir.rose;

    final linePaint = Paint()
      ..color = lineColor
      ..strokeWidth = 2
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;

    final fillPaint = Paint()
      ..shader = LinearGradient(
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
        colors: [lineColor.withOpacity(0.25), lineColor.withOpacity(0.0)],
      ).createShader(Rect.fromLTWH(0, 0, size.width, size.height));

    final path = Path();
    final fillPath = Path();

    for (int i = 0; i < prices.length; i++) {
      final x = (i / (prices.length - 1)) * size.width;
      final y = size.height - ((prices[i] - minP) / range) * size.height;
      if (i == 0) {
        path.moveTo(x, y);
        fillPath.moveTo(x, size.height);
        fillPath.lineTo(x, y);
      } else {
        path.lineTo(x, y);
        fillPath.lineTo(x, y);
      }
    }

    fillPath.lineTo(size.width, size.height);
    fillPath.close();
    canvas.drawPath(fillPath, fillPaint);
    canvas.drawPath(path, linePaint);

    final lastX = size.width;
    final lastY =
        size.height - ((prices.last - minP) / range) * size.height;
    canvas.drawCircle(Offset(lastX, lastY), 3, Paint()..color = lineColor);
    canvas.drawCircle(
        Offset(lastX, lastY), 6, Paint()..color = lineColor.withOpacity(0.2));

    final style = TextStyle(color: Noir.textLow, fontSize: 9);
    _drawText(
        canvas, '\$${maxP.toStringAsFixed(2)}', const Offset(4, 2), style);
    _drawText(canvas, '\$${minP.toStringAsFixed(2)}',
        Offset(4, size.height - 14), style);
    _drawText(
        canvas,
        '\$${prices.last.toStringAsFixed(2)}',
        Offset(size.width - 55, lastY - 14),
        TextStyle(
            color: lineColor, fontSize: 10, fontWeight: FontWeight.w700));
  }

  void _drawText(Canvas canvas, String text, Offset offset, TextStyle style) {
    final tp = TextPainter(
        text: TextSpan(text: text, style: style),
        textDirection: TextDirection.ltr);
    tp.layout();
    tp.paint(canvas, offset);
  }

  @override
  bool shouldRepaint(covariant PriceChartPainter old) =>
      prices.length != old.prices.length;
}
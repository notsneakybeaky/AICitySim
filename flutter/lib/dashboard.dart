import 'package:flutter/material.dart';
import 'models.dart';
import 'world_service.dart';

class WorldDashboard extends StatefulWidget {
  const WorldDashboard({super.key});

  @override
  State<WorldDashboard> createState() => _WorldDashboardState();
}

class _WorldDashboardState extends State<WorldDashboard> {
  final WorldService _ws = WorldService();

  // ---- Accumulated state ----
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
      // ---- Handshake Ack ----
        case S2C.handshakeAck:
          _clientId = pkt.data['client_id'];
          _tick = pkt.data['current_tick'] ?? 0;
          _activeModules = List<String>.from(pkt.data['active_modules'] ?? []);
          _phase = pkt.data['state'] ?? 'SPECTATE';
          _connected = true;

      // ---- World Snapshot (initial state) ----
        case S2C.worldSnapshot:
          _tick = pkt.data['tick'] ?? _tick;
          _parseCities(pkt.data['world']);
          if (pkt.data['economy'] != null) {
            _economy = EconomyState.fromJson(pkt.data['economy']);
          }

      // ---- Phase Change ----
        case S2C.phaseChange:
          _tick = pkt.data['tick'] ?? _tick;
          _phase = pkt.data['phase'] ?? _phase;

      // ---- Round Result (the big payload each tick) ----
        case S2C.roundResult:
          _tick = pkt.data['tick'] ?? _tick;
          _parseCities(pkt.data['world']);
          if (pkt.data['economy'] != null) {
            _economy = EconomyState.fromJson(pkt.data['economy']);
            _priceHistory.add(_economy.promptPrice);
            // Keep last 100 prices
            if (_priceHistory.length > 100) {
              _priceHistory = _priceHistory.sublist(_priceHistory.length - 100);
            }
          }
          final rawEvents = pkt.data['events'] as List<dynamic>? ?? [];
          _events = rawEvents
              .map((e) => EventEntry.fromJson(e as Map<String, dynamic>))
              .toList();
          _phase = 'TICK_COMPLETE';

      // ---- City Update (delta) ----
        case S2C.cityUpdate:
          final cityId = pkt.data['city_id'] as String?;
          final cityData = pkt.data['city'] as Map<String, dynamic>?;
          if (cityId != null && cityData != null) {
            _cities[cityId] = City.fromJson(cityId, cityData);
          }

      // ---- Economy Tick ----
        case S2C.economyTick:
        // Lightweight economy update
          _tick = pkt.data['tick'] ?? _tick;

      // ---- Event Log ----
        case S2C.eventLog:
          final rawEvents = pkt.data['events'] as List<dynamic>? ?? [];
          _events = rawEvents
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
  //  BUILD
  // ==================================================================

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0A0E12),
      body: Column(
        children: [
          _buildHeader(),
          Expanded(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Left: Cities
                Expanded(flex: 3, child: _buildCityPanel()),
                // Right: Economy + Agents + Events
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
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
      decoration: BoxDecoration(
        color: Colors.black38,
        border: Border(bottom: BorderSide(color: _phaseColor(), width: 2)),
      ),
      child: Row(
        children: [
          const Text(
            'HYPERINFLATION',
            style: TextStyle(
              color: Colors.white,
              fontSize: 18,
              fontWeight: FontWeight.w900,
              letterSpacing: 3,
            ),
          ),
          const SizedBox(width: 16),
          // Connection dot
          Container(
            width: 8,
            height: 8,
            decoration: BoxDecoration(
              color: _connected ? Colors.greenAccent : Colors.redAccent,
              shape: BoxShape.circle,
            ),
          ),
          const SizedBox(width: 8),
          Text(
            _connected ? (_clientId ?? 'connected') : 'disconnected',
            style: TextStyle(
              color: _connected ? Colors.white38 : Colors.redAccent,
              fontSize: 11,
            ),
          ),
          const Spacer(),
          _phasePill(),
          const SizedBox(width: 20),
          _headerStat('TICK', '$_tick'),
          const SizedBox(width: 20),
          _headerStat(
            'PROMPT \$',
            _economy.promptPrice.toStringAsFixed(2),
            valueColor: Colors.greenAccent,
            fontSize: 20,
          ),
          const SizedBox(width: 20),
          _headerStat('DEMAND', '${_economy.currentDemand}'),
          const SizedBox(width: 20),
          _headerStat('SUPPLY', '${_economy.totalSupply}'),
          if (_activeModules.isNotEmpty) ...[
            const SizedBox(width: 20),
            _headerStat('MODULES', _activeModules.join(', ')),
          ],
        ],
      ),
    );
  }

  Widget _phasePill() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 5),
      decoration: BoxDecoration(
        color: _phaseColor().withOpacity(0.15),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: _phaseColor(), width: 1),
      ),
      child: Text(
        _phase,
        style: TextStyle(
          color: _phaseColor(),
          fontWeight: FontWeight.bold,
          fontSize: 12,
          letterSpacing: 1,
        ),
      ),
    );
  }

  Widget _headerStat(String label, String value,
      {Color valueColor = Colors.white, double fontSize = 14}) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.end,
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(label,
            style: const TextStyle(
                color: Colors.white30, fontSize: 9, letterSpacing: 1)),
        Text(value,
            style: TextStyle(
                color: valueColor,
                fontSize: fontSize,
                fontWeight: FontWeight.bold)),
      ],
    );
  }

  Color _phaseColor() {
    switch (_phase) {
      case 'COLLECTING':
        return Colors.cyanAccent;
      case 'PROCESSING':
        return Colors.amberAccent;
      case 'BROADCASTING':
        return Colors.purpleAccent;
      case 'TICK_COMPLETE':
        return Colors.greenAccent;
      case 'DISCONNECTED':
        return Colors.redAccent;
      default:
        return Colors.white38;
    }
  }

  // ==================================================================
  //  CITY PANEL (LEFT)
  // ==================================================================

  Widget _buildCityPanel() {
    if (_cities.isEmpty) {
      return const Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.location_city, color: Colors.white24, size: 48),
            SizedBox(height: 12),
            Text('Waiting for world data...',
                style: TextStyle(color: Colors.white38, fontSize: 14)),
          ],
        ),
      );
    }

    final cities = _cities.values.toList();

    return ListView.builder(
      padding: const EdgeInsets.all(12),
      itemCount: cities.length,
      itemBuilder: (context, i) => _buildCityCard(cities[i]),
    );
  }

  Widget _buildCityCard(City city) {
    final healthColor = _statColor(city.happiness);
    final infraColor = _statColor(city.infrastructure);
    final defenseColor = _statColor(city.digitalDefenses);

    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white.withOpacity(0.04),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: Colors.white12, width: 1),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // City name + population
          Row(
            children: [
              const Icon(Icons.location_city,
                  color: Colors.cyanAccent, size: 18),
              const SizedBox(width: 8),
              Text(
                city.name.toUpperCase(),
                style: const TextStyle(
                  color: Colors.cyanAccent,
                  fontWeight: FontWeight.bold,
                  fontSize: 14,
                  letterSpacing: 1,
                ),
              ),
              const SizedBox(width: 8),
              Text(city.id,
                  style:
                  const TextStyle(color: Colors.white24, fontSize: 10)),
              const Spacer(),
              Text(
                '${_formatPop(city.population)} pop',
                style: const TextStyle(
                    color: Colors.white54,
                    fontSize: 12,
                    fontFamily: 'monospace'),
              ),
            ],
          ),

          const SizedBox(height: 10),

          // Stat bars
          _statBar('Happiness', city.happiness, healthColor),
          _statBar('Infrastructure', city.infrastructure, infraColor),
          _statBar('Defenses', city.digitalDefenses, defenseColor),
          _statBar('Social Cohesion', city.socialCohesion,
              _statColor(city.socialCohesion)),
          _statBar('Employment', city.employmentRate,
              _statColor(city.employmentRate)),

          const SizedBox(height: 8),

          // Treasury + GDP
          Row(
            children: [
              _miniStat('Treasury',
                  '\$${city.treasury.toStringAsFixed(1)}', Colors.amberAccent),
              const SizedBox(width: 16),
              _miniStat('GDP/tick',
                  city.economicOutput.toStringAsFixed(2), Colors.greenAccent),
              const SizedBox(width: 16),
              _miniStat(
                  'Tax', '${(city.taxRate * 100).toStringAsFixed(0)}%',
                  Colors.white54),
              const Spacer(),
              Text(
                'Region: ${city.regionId}',
                style: const TextStyle(color: Colors.white24, fontSize: 10),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _statBar(String label, double value, Color color) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        children: [
          SizedBox(
            width: 110,
            child: Text(label,
                style: const TextStyle(color: Colors.white38, fontSize: 11)),
          ),
          Expanded(
            child: ClipRRect(
              borderRadius: BorderRadius.circular(3),
              child: LinearProgressIndicator(
                value: (value / 100).clamp(0.0, 1.0),
                backgroundColor: Colors.white10,
                valueColor: AlwaysStoppedAnimation(color.withOpacity(0.8)),
                minHeight: 6,
              ),
            ),
          ),
          const SizedBox(width: 8),
          SizedBox(
            width: 40,
            child: Text(
              value.toStringAsFixed(1),
              textAlign: TextAlign.right,
              style: TextStyle(
                  color: color,
                  fontSize: 11,
                  fontFamily: 'monospace',
                  fontWeight: FontWeight.bold),
            ),
          ),
        ],
      ),
    );
  }

  Widget _miniStat(String label, String value, Color color) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label,
            style: const TextStyle(
                color: Colors.white24, fontSize: 9, letterSpacing: 0.5)),
        Text(value,
            style: TextStyle(
                color: color,
                fontSize: 13,
                fontWeight: FontWeight.bold,
                fontFamily: 'monospace')),
      ],
    );
  }

  Color _statColor(double v) {
    if (v >= 70) return Colors.greenAccent;
    if (v >= 40) return Colors.amberAccent;
    return Colors.redAccent;
  }

  String _formatPop(int pop) {
    if (pop >= 1000000) return '${(pop / 1000000).toStringAsFixed(1)}M';
    if (pop >= 1000) return '${(pop / 1000).toStringAsFixed(0)}K';
    return '$pop';
  }

  // ==================================================================
  //  SIDE PANEL (RIGHT)
  // ==================================================================

  Widget _buildSidePanel() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Economy section
          _sectionTitle('PROMPT MARKET'),
          const SizedBox(height: 6),
          _economyRow('Price', '\$${_economy.promptPrice.toStringAsFixed(2)}',
              Colors.greenAccent),
          _economyRow('Demand', '${_economy.currentDemand}', Colors.cyanAccent),
          _economyRow('Supply', '${_economy.totalSupply}', Colors.white54),
          _economyRow(
              'Market Value',
              '\$${_economy.totalMarketValue.toStringAsFixed(0)}',
              Colors.amberAccent),
          _economyRow(
              'Open Orders', '${_economy.openOrders}', Colors.white38),

          const SizedBox(height: 12),

          // Price chart
          if (_priceHistory.length >= 2) ...[
            _sectionTitle('PRICE HISTORY'),
            const SizedBox(height: 6),
            SizedBox(
              height: 100,
              child: CustomPaint(
                size: const Size(double.infinity, 100),
                painter: _PriceChartPainter(_priceHistory),
              ),
            ),
            const SizedBox(height: 12),
          ],

          // Agents section
          _sectionTitle('AGENTS'),
          const SizedBox(height: 6),
          ..._economy.agentEconomies.entries.map((e) {
            final id = e.key;
            final econ = e.value;
            final color = Color(agentColors[id] ?? 0xFF888888);
            final name = agentNames[id] ?? id;

            return Container(
              margin: const EdgeInsets.only(bottom: 6),
              padding:
              const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
              decoration: BoxDecoration(
                color: econ.inDebt
                    ? Colors.redAccent.withOpacity(0.08)
                    : Colors.white.withOpacity(0.03),
                borderRadius: BorderRadius.circular(6),
                border: Border.all(
                  color: econ.inDebt ? Colors.redAccent.withOpacity(0.3) : Colors.white10,
                  width: 1,
                ),
              ),
              child: Row(
                children: [
                  Container(
                    width: 8,
                    height: 8,
                    decoration:
                    BoxDecoration(color: color, shape: BoxShape.circle),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(name,
                            style: TextStyle(
                                color: color,
                                fontSize: 12,
                                fontWeight: FontWeight.bold)),
                        Text(
                          'Prompts: ${econ.allocatedPrompts}  •  Served: ${econ.promptsServed}',
                          style: const TextStyle(
                              color: Colors.white30, fontSize: 10),
                        ),
                      ],
                    ),
                  ),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      Text(
                        '\$${econ.wallet.toStringAsFixed(1)}',
                        style: TextStyle(
                          color: econ.inDebt
                              ? Colors.redAccent
                              : Colors.greenAccent,
                          fontWeight: FontWeight.bold,
                          fontSize: 13,
                          fontFamily: 'monospace',
                        ),
                      ),
                      if (econ.inDebt)
                        const Text('IN DEBT',
                            style: TextStyle(
                                color: Colors.redAccent,
                                fontSize: 9,
                                fontWeight: FontWeight.bold)),
                    ],
                  ),
                ],
              ),
            );
          }),

          const SizedBox(height: 16),

          // Events section
          _sectionTitle('EVENTS (TICK $_tick)'),
          const SizedBox(height: 6),
          if (_events.isEmpty)
            const Text('No events yet.',
                style: TextStyle(color: Colors.white24, fontSize: 11))
          else
            ..._events.map((evt) {
              final eventColor = _eventColor(evt.type);
              final agentColor = evt.agent != null
                  ? Color(agentColors[evt.agent] ?? 0xFF888888)
                  : Colors.white38;

              return Container(
                margin: const EdgeInsets.only(bottom: 4),
                padding:
                const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
                decoration: BoxDecoration(
                  color: eventColor.withOpacity(0.06),
                  borderRadius: BorderRadius.circular(4),
                  border:
                  Border(left: BorderSide(color: eventColor, width: 3)),
                ),
                child: Row(
                  children: [
                    if (evt.agent != null) ...[
                      Container(
                        width: 6,
                        height: 6,
                        decoration: BoxDecoration(
                            color: agentColor, shape: BoxShape.circle),
                      ),
                      const SizedBox(width: 6),
                    ],
                    Expanded(
                      child: Text(
                        evt.description,
                        style: const TextStyle(
                            color: Colors.white60, fontSize: 11),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    const SizedBox(width: 6),
                    Text(
                      evt.type,
                      style: TextStyle(
                        color: eventColor,
                        fontSize: 9,
                        fontWeight: FontWeight.bold,
                        letterSpacing: 0.5,
                      ),
                    ),
                  ],
                ),
              );
            }),
        ],
      ),
    );
  }

  Widget _sectionTitle(String text) {
    return Text(
      text,
      style: const TextStyle(
        color: Colors.white30,
        fontSize: 11,
        fontWeight: FontWeight.bold,
        letterSpacing: 2,
      ),
    );
  }

  Widget _economyRow(String label, String value, Color color) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label,
              style: const TextStyle(color: Colors.white38, fontSize: 12)),
          Text(value,
              style: TextStyle(
                  color: color,
                  fontSize: 13,
                  fontWeight: FontWeight.bold,
                  fontFamily: 'monospace')),
        ],
      ),
    );
  }

  Color _eventColor(String type) {
    if (type.contains('ATTACK') || type.contains('DAMAGE')) {
      return Colors.redAccent;
    }
    if (type.contains('BUILD') || type.contains('BOOST') ||
        type.contains('DEFEND')) {
      return Colors.greenAccent;
    }
    if (type.contains('BID') || type.contains('ASK') ||
        type.contains('EARN')) {
      return Colors.amberAccent;
    }
    if (type.contains('INFILTRATE') || type.contains('PROPAGANDA')) {
      return Colors.purpleAccent;
    }
    if (type.contains('ALLIANCE')) return Colors.cyanAccent;
    return Colors.white38;
  }
}

// ==================================================================
//  PRICE CHART
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

    final linePaint = Paint()
      ..color = Colors.greenAccent
      ..strokeWidth = 1.5
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round;

    final fillPaint = Paint()
      ..shader = const LinearGradient(
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
        colors: [
          Color(0x4069F0AE),
          Color(0x0069F0AE),
        ],
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

    // Labels
    final style = const TextStyle(color: Colors.white30, fontSize: 9);
    _drawText(canvas, '\$${maxP.toStringAsFixed(2)}', const Offset(2, 0), style);
    _drawText(
        canvas,
        '\$${minP.toStringAsFixed(2)}',
        Offset(2, size.height - 12),
        style);
  }

  void _drawText(Canvas canvas, String text, Offset offset, TextStyle style) {
    final tp = TextPainter(
      text: TextSpan(text: text, style: style),
      textDirection: TextDirection.ltr,
    );
    tp.layout();
    tp.paint(canvas, offset);
  }

  @override
  bool shouldRepaint(covariant _PriceChartPainter old) =>
      prices.length != old.prices.length;
}
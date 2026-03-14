import 'package:flutter/material.dart';
import 'models.dart';
import 'market_service.dart';

class SwarmDashboard extends StatefulWidget {
  @override
  State<SwarmDashboard> createState() => _SwarmDashboardState();
}

class _SwarmDashboardState extends State<SwarmDashboard> {
  final MarketService _market = MarketService();

  // ---- Accumulated state ----
  int _round = 0;
  String _phase = 'CONNECTING';
  MarketRegime _regime = MarketRegime();
  List<Proposal> _proposals = [];
  String? _winnerAgent;
  Proposal? _winningPolicy;
  List<RoundResult> _history = [];
  List<double> _priceHistory = [];

  @override
  void initState() {
    super.initState();
    _market.connect().listen(_onMessage);
  }

  @override
  void dispose() {
    _market.dispose();
    super.dispose();
  }

  void _onMessage(Map<String, dynamic> data) {
    final type = data['type'] as String? ?? '';

    setState(() {
      // Always update round if present
      if (data.containsKey('round')) {
        _round = data['round'];
      }

      // Always update regime if present
      if (data.containsKey('regime')) {
        _regime = MarketRegime.fromJson(data['regime']);
      }

      switch (type) {
        case 'MARKET_STATE':
          _phase = data['phase'] ?? 'IDLE';
          break;

        case 'PHASE_CHANGE':
          _phase = data['phase'] ?? _phase;
          // When a new round starts proposing, clear old proposals
          if (_phase == 'PROPOSING') {
            _proposals = [];
            _winnerAgent = null;
            _winningPolicy = null;
          }
          break;

        case 'PROPOSALS':
          final rawProposals = data['proposals'] as List<dynamic>? ?? [];
          _proposals = rawProposals
              .map((p) => Proposal.fromJson(p as Map<String, dynamic>))
              .toList();
          break;

        case 'ROUND_RESULT':
          final status = data['status'] ?? '';
          if (status == 'APPLIED') {
            _winnerAgent = data['winner_agent'];
            final wp = data['winning_policy'] as Map<String, dynamic>?;
            if (wp != null) {
              _winningPolicy = Proposal.fromJson(wp);
            }
            _priceHistory.add(_regime.price);

            _history.add(RoundResult(
              round: _round,
              winnerAgent: _winnerAgent!,
              winningPolicy: _winningPolicy!,
              regime: _regime,
            ));
          }
          _phase = 'RESULT';
          break;
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
                // Left: Agent proposals
                Expanded(flex: 3, child: _buildProposalPanel()),
                // Right: Market regime + history
                Expanded(flex: 2, child: _buildMarketPanel()),
              ],
            ),
          ),
          // Bottom: Winning policy banner
          if (_winningPolicy != null) _buildWinnerBanner(),
        ],
      ),
    );
  }

  // ==================================================================
  //  HEADER
  // ==================================================================

  Widget _buildHeader() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
      decoration: BoxDecoration(
        color: Colors.black38,
        border: Border(bottom: BorderSide(color: _phaseColor(), width: 2)),
      ),
      child: Row(
        children: [
          // Title
          const Text(
            'SWARM POLICY ENGINE',
            style: TextStyle(
              color: Colors.white,
              fontSize: 18,
              fontWeight: FontWeight.w900,
              letterSpacing: 2,
            ),
          ),
          const Spacer(),

          // Phase pill
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
            decoration: BoxDecoration(
              color: _phaseColor().withOpacity(0.2),
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: _phaseColor(), width: 1),
            ),
            child: Text(
              _phase,
              style: TextStyle(
                color: _phaseColor(),
                fontWeight: FontWeight.bold,
                fontSize: 13,
                letterSpacing: 1,
              ),
            ),
          ),
          const SizedBox(width: 24),

          // Round
          _headerStat('ROUND', '$_round'),
          const SizedBox(width: 24),

          // Price
          _headerStat(
            'PRICE',
            '\$${_regime.price.toStringAsFixed(2)}',
            valueColor: Colors.greenAccent,
            fontSize: 22,
          ),
          const SizedBox(width: 24),

          // Version
          _headerStat('VERSION', '${_regime.version}'),
        ],
      ),
    );
  }

  Widget _headerStat(String label, String value,
      {Color valueColor = Colors.white, double fontSize = 16}) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        Text(label,
            style: const TextStyle(color: Colors.white38, fontSize: 10, letterSpacing: 1)),
        Text(value,
            style: TextStyle(
                color: valueColor, fontSize: fontSize, fontWeight: FontWeight.bold)),
      ],
    );
  }

  Color _phaseColor() {
    switch (_phase) {
      case 'PROPOSING':
        return Colors.cyanAccent;
      case 'EVALUATING':
        return Colors.amberAccent;
      case 'MEDIATING':
        return Colors.purpleAccent;
      case 'RESULT':
        return Colors.greenAccent;
      default:
        return Colors.white38;
    }
  }

  // ==================================================================
  //  PROPOSAL PANEL (LEFT)
  // ==================================================================

  Widget _buildProposalPanel() {
    if (_proposals.isEmpty) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              _phase == 'PROPOSING' ? Icons.hourglass_top : Icons.smart_toy,
              color: Colors.white24,
              size: 48,
            ),
            const SizedBox(height: 12),
            Text(
              _phase == 'PROPOSING'
                  ? 'Agents are thinking...'
                  : 'Waiting for next round',
              style: const TextStyle(color: Colors.white38, fontSize: 14),
            ),
          ],
        ),
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.all(12),
      itemCount: _proposals.length,
      itemBuilder: (context, i) {
        final p = _proposals[i];
        final isWinner = p.agentId == _winnerAgent;
        final color = Color(agentColors[p.agentId] ?? 0xFF888888);
        final personality = agentPersonalities[p.agentId] ?? p.agentId;

        return Container(
          margin: const EdgeInsets.only(bottom: 8),
          decoration: BoxDecoration(
            color: isWinner
                ? color.withOpacity(0.15)
                : Colors.white.withOpacity(0.04),
            borderRadius: BorderRadius.circular(8),
            border: Border.all(
              color: isWinner ? color : Colors.white12,
              width: isWinner ? 2 : 1,
            ),
          ),
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Agent header row
                Row(
                  children: [
                    // Agent color dot
                    Container(
                      width: 10,
                      height: 10,
                      decoration: BoxDecoration(
                        color: color,
                        shape: BoxShape.circle,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Text(
                      personality.toUpperCase(),
                      style: TextStyle(
                        color: color,
                        fontWeight: FontWeight.bold,
                        fontSize: 12,
                        letterSpacing: 1,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Text(
                      p.agentId,
                      style: const TextStyle(color: Colors.white24, fontSize: 11),
                    ),
                    const Spacer(),
                    if (isWinner)
                      Container(
                        padding:
                        const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                        decoration: BoxDecoration(
                          color: Colors.greenAccent.withOpacity(0.2),
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: const Text(
                          '★ WINNER',
                          style: TextStyle(
                            color: Colors.greenAccent,
                            fontSize: 10,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                    const SizedBox(width: 8),
                    // Satisfaction
                    Text(
                      '${(p.satisfaction * 100).toInt()}%',
                      style: TextStyle(
                        color: color,
                        fontWeight: FontWeight.bold,
                        fontSize: 14,
                      ),
                    ),
                  ],
                ),

                const SizedBox(height: 8),

                // Satisfaction bar
                ClipRRect(
                  borderRadius: BorderRadius.circular(4),
                  child: LinearProgressIndicator(
                    value: p.satisfaction,
                    backgroundColor: Colors.white10,
                    valueColor: AlwaysStoppedAnimation(color.withOpacity(0.7)),
                    minHeight: 3,
                  ),
                ),

                const SizedBox(height: 8),

                // Proposal text
                Text(
                  p.proposedText,
                  style: const TextStyle(color: Colors.white70, fontSize: 12, height: 1.4),
                  maxLines: 4,
                  overflow: TextOverflow.ellipsis,
                ),

                const SizedBox(height: 8),

                // Params row
                Wrap(
                  spacing: 8,
                  runSpacing: 4,
                  children: p.params.entries.map((e) {
                    return _paramChip(e.key, e.value);
                  }).toList(),
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _paramChip(String label, double value) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: Colors.white.withOpacity(0.06),
        borderRadius: BorderRadius.circular(4),
      ),
      child: Text(
        '$label: ${value.toStringAsFixed(2)}',
        style: const TextStyle(color: Colors.white38, fontSize: 10, fontFamily: 'monospace'),
      ),
    );
  }

  // ==================================================================
  //  MARKET PANEL (RIGHT)
  // ==================================================================

  Widget _buildMarketPanel() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Regime params
          _sectionTitle('MARKET REGIME'),
          const SizedBox(height: 8),
          _regimeRow('Price', '\$${_regime.price.toStringAsFixed(2)}', Colors.greenAccent),
          _regimeRow('Drift', _regime.drift.toStringAsFixed(3), _driftColor()),
          _regimeRow('Volatility', _regime.volatility.toStringAsFixed(3), _volColor()),
          _regimeRow('Liquidity', _regime.liquidity.toStringAsFixed(3), Colors.cyanAccent),
          _regimeRow('Spread (bps)', _regime.spreadBps.toStringAsFixed(1), Colors.white54),
          _regimeRow('Shock Prob', _regime.shockProb.toStringAsFixed(4), Colors.redAccent),

          const SizedBox(height: 24),

          // Price history
          _sectionTitle('PRICE HISTORY'),
          const SizedBox(height: 8),
          if (_priceHistory.length >= 2)
            SizedBox(
              height: 120,
              child: CustomPaint(
                size: const Size(double.infinity, 120),
                painter: _PriceChartPainter(_priceHistory),
              ),
            )
          else
            const Text(
              'Waiting for data...',
              style: TextStyle(color: Colors.white24, fontSize: 12),
            ),

          const SizedBox(height: 24),

          // Round history
          _sectionTitle('ROUND HISTORY'),
          const SizedBox(height: 8),
          ..._history.reversed.take(10).map((r) {
            final color = Color(agentColors[r.winnerAgent] ?? 0xFF888888);
            final personality = agentPersonalities[r.winnerAgent] ?? r.winnerAgent;
            return Container(
              margin: const EdgeInsets.only(bottom: 4),
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
              decoration: BoxDecoration(
                color: Colors.white.withOpacity(0.04),
                borderRadius: BorderRadius.circular(4),
              ),
              child: Row(
                children: [
                  Text(
                    'R${r.round}',
                    style: const TextStyle(
                        color: Colors.white38, fontSize: 11, fontFamily: 'monospace'),
                  ),
                  const SizedBox(width: 8),
                  Container(
                    width: 8,
                    height: 8,
                    decoration: BoxDecoration(color: color, shape: BoxShape.circle),
                  ),
                  const SizedBox(width: 6),
                  Expanded(
                    child: Text(
                      personality,
                      style: TextStyle(color: color, fontSize: 11),
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  Text(
                    '\$${r.regime.price.toStringAsFixed(2)}',
                    style: const TextStyle(
                        color: Colors.greenAccent, fontSize: 11, fontFamily: 'monospace'),
                  ),
                ],
              ),
            );
          }).toList(),
        ],
      ),
    );
  }

  Widget _sectionTitle(String text) {
    return Text(
      text,
      style: const TextStyle(
        color: Colors.white38,
        fontSize: 11,
        fontWeight: FontWeight.bold,
        letterSpacing: 2,
      ),
    );
  }

  Widget _regimeRow(String label, String value, Color valueColor) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(color: Colors.white54, fontSize: 13)),
          Text(
            value,
            style: TextStyle(
              color: valueColor,
              fontSize: 14,
              fontWeight: FontWeight.bold,
              fontFamily: 'monospace',
            ),
          ),
        ],
      ),
    );
  }

  Color _driftColor() =>
      _regime.drift >= 0 ? Colors.greenAccent : Colors.redAccent;

  Color _volColor() {
    if (_regime.volatility < 0.5) return Colors.greenAccent;
    if (_regime.volatility < 2.0) return Colors.amberAccent;
    return Colors.redAccent;
  }

  // ==================================================================
  //  WINNER BANNER (BOTTOM)
  // ==================================================================

  Widget _buildWinnerBanner() {
    final color = Color(agentColors[_winnerAgent] ?? 0xFF888888);
    final personality = agentPersonalities[_winnerAgent] ?? _winnerAgent ?? '';

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        border: Border(top: BorderSide(color: color, width: 2)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Row(
            children: [
              const Text(
                '★ WINNING POLICY',
                style: TextStyle(
                  color: Colors.greenAccent,
                  fontSize: 11,
                  fontWeight: FontWeight.bold,
                  letterSpacing: 2,
                ),
              ),
              const SizedBox(width: 12),
              Text(
                'Round $_round  •  $personality',
                style: TextStyle(color: color, fontSize: 12, fontWeight: FontWeight.w600),
              ),
            ],
          ),
          const SizedBox(height: 6),
          Text(
            _winningPolicy!.proposedText,
            style: const TextStyle(color: Colors.white70, fontSize: 12, height: 1.4),
            maxLines: 3,
            overflow: TextOverflow.ellipsis,
          ),
        ],
      ),
    );
  }
}

// ==================================================================
//  SIMPLE PRICE CHART PAINTER
// ==================================================================

class _PriceChartPainter extends CustomPainter {
  final List<double> prices;

  _PriceChartPainter(this.prices);

  @override
  void paint(Canvas canvas, Size size) {
    if (prices.length < 2) return;

    final minP = prices.reduce((a, b) => a < b ? a : b) * 0.98;
    final maxP = prices.reduce((a, b) => a > b ? a : b) * 1.02;
    final range = maxP - minP;
    if (range == 0) return;

    final paint = Paint()
      ..color = Colors.greenAccent
      ..strokeWidth = 2
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round;

    final fillPaint = Paint()
      ..shader = LinearGradient(
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
        colors: [
          Colors.greenAccent.withOpacity(0.3),
          Colors.greenAccent.withOpacity(0.0),
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
    canvas.drawPath(path, paint);

    // Draw price labels
    final textStyle = TextStyle(color: Colors.white38, fontSize: 9);
    _drawText(canvas, '\$${maxP.toStringAsFixed(1)}', Offset(2, 2), textStyle);
    _drawText(canvas, '\$${minP.toStringAsFixed(1)}', Offset(2, size.height - 14), textStyle);
  }

  void _drawText(Canvas canvas, String text, Offset offset, TextStyle style) {
    final span = TextSpan(text: text, style: style);
    final tp = TextPainter(text: span, textDirection: TextDirection.ltr);
    tp.layout();
    tp.paint(canvas, offset);
  }

  @override
  bool shouldRepaint(covariant _PriceChartPainter old) =>
      old.prices.length != prices.length ||
          (prices.isNotEmpty && old.prices.isNotEmpty && old.prices.last != prices.last);
}
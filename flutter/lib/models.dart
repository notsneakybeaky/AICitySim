class MarketRegime {
  final double price;
  final double drift;
  final double volatility;
  final double liquidity;
  final double spreadBps;
  final double shockProb;
  final int version;
  final int updatedAtMs;

  MarketRegime({
    this.price = 0.0,
    this.drift = 0.0,
    this.volatility = 0.0,
    this.liquidity = 0.0,
    this.spreadBps = 0.0,
    this.shockProb = 0.0,
    this.version = 0,
    this.updatedAtMs = 0,
  });

  factory MarketRegime.fromJson(Map<String, dynamic> json) {
    return MarketRegime(
      price: (json['price'] ?? 0).toDouble(),
      drift: (json['drift'] ?? 0).toDouble(),
      volatility: (json['volatility'] ?? 0).toDouble(),
      liquidity: (json['liquidity'] ?? 0).toDouble(),
      spreadBps: (json['spread_bps'] ?? 0).toDouble(),
      shockProb: (json['shock_prob'] ?? 0).toDouble(),
      version: json['version'] ?? 0,
      updatedAtMs: json['updated_at_ms'] ?? 0,
    );
  }
}

class Proposal {
  final String agentId;
  final String proposedText;
  final Map<String, double> params;
  final double satisfaction;

  Proposal({
    required this.agentId,
    required this.proposedText,
    required this.params,
    required this.satisfaction,
  });

  factory Proposal.fromJson(Map<String, dynamic> json) {
    // 1. Safely extract the map as a generic Map first
    final rawParams = json['params'] as Map<String, dynamic>? ?? {};

    // 2. Convert values to double by casting to 'num' first to avoid type errors
    final params = rawParams.map((k, v) {
      return MapEntry(k, (v as num? ?? 0).toDouble());
    });

    return Proposal(
      agentId: json['agentId'] ?? '',
      proposedText: json['proposedText'] ?? '',
      params: params,
      satisfaction: (json['satisfaction'] as num? ?? 0).toDouble(),
    );
  }
}

class RoundResult {
  final int round;
  final String winnerAgent;
  final Proposal winningPolicy;
  final MarketRegime regime;

  RoundResult({
    required this.round,
    required this.winnerAgent,
    required this.winningPolicy,
    required this.regime,
  });
}

/// Agent personality labels for display
const agentPersonalities = {
  'agent-0': 'Blue-Collar Worker',
  'agent-1': 'Corporate CEO',
  'agent-2': 'Career Politician',
  'agent-3': 'Parent',
  'agent-4': 'Small Biz Owner',
};

/// Agent colors for visual distinction
const agentColors = {
  'agent-0': 0xFF42A5F5, // blue
  'agent-1': 0xFFEF5350, // red
  'agent-2': 0xFFAB47BC, // purple
  'agent-3': 0xFF66BB6A, // green
  'agent-4': 0xFFFFA726, // orange
};
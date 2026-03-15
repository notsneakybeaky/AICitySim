class City {
  final String id, name, regionId;
  final int tileX, tileY, population;
  final double happiness, employmentRate, infrastructure;
  final double digitalDefenses, socialCohesion;
  final double treasury, economicOutput, taxRate;

  City({required this.id, required this.name, this.regionId = '', this.tileX = 0, this.tileY = 0, this.population = 0, this.happiness = 0, this.employmentRate = 0, this.infrastructure = 0, this.digitalDefenses = 0, this.socialCohesion = 0, this.treasury = 0, this.economicOutput = 0, this.taxRate = 0});

  factory City.fromJson(String id, Map<String, dynamic> json) => City(
    id: id, name: json['name'] ?? id, regionId: json['region_id'] ?? '',
    tileX: json['tile_x'] ?? 0, tileY: json['tile_y'] ?? 0, population: json['population'] ?? 0,
    happiness: _d(json['happiness']), employmentRate: _d(json['employment_rate']),
    infrastructure: _d(json['infrastructure']), digitalDefenses: _d(json['digital_defenses']),
    socialCohesion: _d(json['social_cohesion']), treasury: _d(json['treasury']),
    economicOutput: _d(json['economic_output']), taxRate: _d(json['tax_rate']),
  );
}

class AgentEconomy {
  final double wallet, totalEarnings, totalSpending;
  final int allocatedPrompts, promptsServed;
  final bool inDebt;
  AgentEconomy({this.wallet = 0, this.allocatedPrompts = 0, this.promptsServed = 0, this.totalEarnings = 0, this.totalSpending = 0, this.inDebt = false});
  factory AgentEconomy.fromJson(Map<String, dynamic> json) => AgentEconomy(
    wallet: _d(json['wallet']), allocatedPrompts: json['allocated_prompts'] ?? 0,
    promptsServed: json['prompts_served'] ?? 0, totalEarnings: _d(json['total_earnings']),
    totalSpending: _d(json['total_spending']), inDebt: json['in_debt'] ?? false,
  );
}

class EconomyState {
  final double promptPrice, totalMarketValue;
  final int totalSupply, currentDemand, openOrders;
  final Map<String, AgentEconomy> agentEconomies;
  EconomyState({this.promptPrice = 1.0, this.totalSupply = 0, this.currentDemand = 0, this.totalMarketValue = 1000.0, this.openOrders = 0, this.agentEconomies = const {}});
  factory EconomyState.fromJson(Map<String, dynamic> json) {
    final market = json['market'] as Map<String, dynamic>? ?? {};
    final agentsRaw = json['agents'] as Map<String, dynamic>? ?? {};
    final agents = <String, AgentEconomy>{};
    agentsRaw.forEach((id, data) { if (data is Map<String, dynamic>) agents[id] = AgentEconomy.fromJson(data); });
    return EconomyState(promptPrice: _d(market['price'], fallback: 1.0), totalSupply: market['total_supply'] ?? 0, currentDemand: market['current_demand'] ?? 0, totalMarketValue: _d(market['total_market_value'], fallback: 1000.0), openOrders: market['open_orders'] ?? 0, agentEconomies: agents);
  }
}

class EventEntry {
  final String type, description;
  final String? source, agent, target, sourceCity;
  final int ts;
  EventEntry({required this.type, this.source, this.agent, this.target, this.sourceCity, required this.description, this.ts = 0});
  factory EventEntry.fromJson(Map<String, dynamic> json) => EventEntry(
    type: json['type'] ?? 'UNKNOWN', source: json['source'], agent: json['agent'],
    target: json['target'], sourceCity: json['source_city']?.toString(),
    description: json['description'] ?? '', ts: json['ts'] ?? 0,
  );
}

/// Compact grid: terrain + ownership as strings, row-major (index = y * width + x)
class GridSnapshot {
  final int width, height;
  final String terrain; // P F M W D U I
  final String owners;  // first char of city id, or '.'
  GridSnapshot({required this.width, required this.height, required this.terrain, required this.owners});

  factory GridSnapshot.fromJson(Map<String, dynamic> json) => GridSnapshot(
    width: json['width'] ?? 25, height: json['height'] ?? 22,
    terrain: json['terrain']?.toString() ?? '', owners: json['owners']?.toString() ?? '',
  );

  bool get isEmpty => terrain.isEmpty;

  GridSnapshot applyChanges(List<dynamic> changes) {
    if (terrain.isEmpty) return this;
    final tBuf = List<int>.from(terrain.codeUnits);
    final oBuf = List<int>.from(owners.codeUnits);
    for (final c in changes) {
      if (c is! Map<String, dynamic>) continue;
      final x = (c['x'] as num?)?.toInt() ?? 0;
      final y = (c['y'] as num?)?.toInt() ?? 0;
      final idx = y * width + x;
      if (idx < 0 || idx >= tBuf.length) continue;
      final tName = c['terrain'];
      if (tName is String && tName.isNotEmpty) tBuf[idx] = _tChar(tName);
      if (c.containsKey('owner')) {
        final o = c['owner'];
        oBuf[idx] = (o is String && o.isNotEmpty) ? o.codeUnitAt(0) : 46; // '.'
      }
    }
    return GridSnapshot(width: width, height: height, terrain: String.fromCharCodes(tBuf), owners: String.fromCharCodes(oBuf));
  }

  static int _tChar(String n) => switch (n) { 'FOREST' => 70, 'MOUNTAIN' => 77, 'WATER' => 87, 'DESERT' => 68, 'URBAN' => 85, 'INDUSTRIAL' => 73, _ => 80 };
}

const agentNames = {'agent-0': 'The Grinder', 'agent-1': 'The Shark', 'agent-2': 'The Diplomat', 'agent-3': 'The Gambler', 'agent-4': 'The Architect'};
double _d(dynamic v, {double fallback = 0.0}) => v is num ? v.toDouble() : fallback;
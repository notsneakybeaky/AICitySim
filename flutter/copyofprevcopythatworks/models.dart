// =============================================================================
//  DATA MODELS — matches the new Netty packet payloads
// =============================================================================

class City {
  final String id;
  final String name;
  final String regionId;
  final int tileX;
  final int tileY;
  final int population;
  final double happiness;
  final double employmentRate;
  final double infrastructure;
  final double digitalDefenses;
  final double socialCohesion;
  final double treasury;
  final double economicOutput;
  final double taxRate;

  City({
    required this.id,
    required this.name,
    this.regionId = '',
    this.tileX = 0,
    this.tileY = 0,
    this.population = 0,
    this.happiness = 0,
    this.employmentRate = 0,
    this.infrastructure = 0,
    this.digitalDefenses = 0,
    this.socialCohesion = 0,
    this.treasury = 0,
    this.economicOutput = 0,
    this.taxRate = 0,
  });

  factory City.fromJson(String id, Map<String, dynamic> json) {
    return City(
      id: id,
      name: json['name'] ?? id,
      regionId: json['region_id'] ?? '',
      tileX: json['tile_x'] ?? 0,
      tileY: json['tile_y'] ?? 0,
      population: json['population'] ?? 0,
      happiness: _d(json['happiness']),
      employmentRate: _d(json['employment_rate']),
      infrastructure: _d(json['infrastructure']),
      digitalDefenses: _d(json['digital_defenses']),
      socialCohesion: _d(json['social_cohesion']),
      treasury: _d(json['treasury']),
      economicOutput: _d(json['economic_output']),
      taxRate: _d(json['tax_rate']),
    );
  }
}

class AgentInfo {
  final String id;
  final String name;
  final String description;
  final String priorities;
  final double aggression;
  final double riskTolerance;
  final double cooperation;
  final bool alive;
  final double wallet;
  final int allocatedPrompts;
  final int promptsServed;
  final bool inDebt;

  AgentInfo({
    required this.id,
    this.name = '',
    this.description = '',
    this.priorities = '',
    this.aggression = 0,
    this.riskTolerance = 0,
    this.cooperation = 0,
    this.alive = true,
    this.wallet = 0,
    this.allocatedPrompts = 0,
    this.promptsServed = 0,
    this.inDebt = false,
  });

  factory AgentInfo.fromJson(String id, Map<String, dynamic> json) {
    return AgentInfo(
      id: id,
      name: json['name'] ?? id,
      description: json['description'] ?? '',
      priorities: json['priorities'] ?? '',
      aggression: _d(json['aggression']),
      riskTolerance: _d(json['risk_tolerance']),
      cooperation: _d(json['cooperation']),
      alive: json['alive'] ?? true,
      wallet: _d(json['wallet']),
      allocatedPrompts: json['allocated_prompts'] ?? 0,
      promptsServed: json['prompts_served'] ?? 0,
      inDebt: json['in_debt'] ?? false,
    );
  }
}

class EconomyState {
  final double promptPrice;
  final int totalSupply;
  final int currentDemand;
  final double totalMarketValue;
  final int openOrders;
  final Map<String, AgentEconomy> agentEconomies;

  EconomyState({
    this.promptPrice = 1.0,
    this.totalSupply = 0,
    this.currentDemand = 0,
    this.totalMarketValue = 1000.0,
    this.openOrders = 0,
    this.agentEconomies = const {},
  });

  factory EconomyState.fromJson(Map<String, dynamic> json) {
    final market = json['market'] as Map<String, dynamic>? ?? {};
    final agentsRaw = json['agents'] as Map<String, dynamic>? ?? {};

    final agents = <String, AgentEconomy>{};
    agentsRaw.forEach((id, data) {
      if (data is Map<String, dynamic>) {
        agents[id] = AgentEconomy.fromJson(data);
      }
    });

    return EconomyState(
      promptPrice: _d(market['price'], fallback: 1.0),
      totalSupply: market['total_supply'] ?? 0,
      currentDemand: market['current_demand'] ?? 0,
      totalMarketValue: _d(market['total_market_value'], fallback: 1000.0),
      openOrders: market['open_orders'] ?? 0,
      agentEconomies: agents,
    );
  }
}

class AgentEconomy {
  final double wallet;
  final int allocatedPrompts;
  final int promptsServed;
  final double totalEarnings;
  final double totalSpending;
  final bool inDebt;

  AgentEconomy({
    this.wallet = 0,
    this.allocatedPrompts = 0,
    this.promptsServed = 0,
    this.totalEarnings = 0,
    this.totalSpending = 0,
    this.inDebt = false,
  });

  factory AgentEconomy.fromJson(Map<String, dynamic> json) {
    return AgentEconomy(
      wallet: _d(json['wallet']),
      allocatedPrompts: json['allocated_prompts'] ?? 0,
      promptsServed: json['prompts_served'] ?? 0,
      totalEarnings: _d(json['total_earnings']),
      totalSpending: _d(json['total_spending']),
      inDebt: json['in_debt'] ?? false,
    );
  }
}

class EventEntry {
  final String type;
  final String? source;
  final String? agent;
  final String? target;
  final String description;
  final int ts;

  EventEntry({
    required this.type,
    this.source,
    this.agent,
    this.target,
    required this.description,
    this.ts = 0,
  });

  factory EventEntry.fromJson(Map<String, dynamic> json) {
    return EventEntry(
      type: json['type'] ?? 'UNKNOWN',
      source: json['source'],
      agent: json['agent'],
      target: json['target'],
      description: json['description'] ?? '',
      ts: json['ts'] ?? 0,
    );
  }
}

// =============================================================================
//  AGENT DISPLAY CONSTANTS
// =============================================================================

const agentNames = {
  'agent-0': 'The Grinder',
  'agent-1': 'The Shark',
  'agent-2': 'The Diplomat',
  'agent-3': 'The Gambler',
  'agent-4': 'The Architect',
};

const agentColors = {
  'agent-0': 0xFF42A5F5, // blue
  'agent-1': 0xFFEF5350, // red
  'agent-2': 0xFFAB47BC, // purple
  'agent-3': 0xFF66BB6A, // green
  'agent-4': 0xFFFFA726, // orange
};

// =============================================================================
//  HELPER
// =============================================================================

double _d(dynamic v, {double fallback = 0.0}) {
  if (v is num) return v.toDouble();
  return fallback;
}
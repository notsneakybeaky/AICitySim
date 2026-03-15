import os
import re
import json
import asyncio

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import google.generativeai as genai
from google.generativeai.types import HarmCategory, HarmBlockThreshold

# ---- Config ----
load_dotenv()
API_KEY = os.environ.get("GOOGLE_GENAI_API_KEY")
if not API_KEY:
    raise RuntimeError("Set GOOGLE_GENAI_API_KEY environment variable")

genai.configure(api_key=API_KEY)
model = genai.GenerativeModel(
    "models/gemini-2.5-flash",
    safety_settings={
        HarmCategory.HARM_CATEGORY_HARASSMENT: HarmBlockThreshold.BLOCK_NONE,
        HarmCategory.HARM_CATEGORY_HATE_SPEECH: HarmBlockThreshold.BLOCK_NONE,
        HarmCategory.HARM_CATEGORY_SEXUALLY_EXPLICIT: HarmBlockThreshold.BLOCK_NONE,
        HarmCategory.HARM_CATEGORY_DANGEROUS_CONTENT: HarmBlockThreshold.BLOCK_NONE,
    }
)

app = FastAPI(title="Hyperinflation AI Service")


# =============================================================================
#  VALID ACTIONS - Must match Action.Type in Java
# =============================================================================

VALID_ACTIONS = {
    "PLACE_BID", "PLACE_ASK", "CANCEL_ORDER",
    "ADJUST_PRICE", "DRAIN_TREASURY", "INJECT_CAPITAL",
    "BUILD_INFRASTRUCTURE", "DAMAGE_INFRASTRUCTURE",
    "BOOST_HAPPINESS", "DAMAGE_HAPPINESS",
    "GROW_POPULATION", "SHRINK_POPULATION",
    "AGENT_EARN", "AGENT_SPEND",
    "FORM_ALLIANCE", "BREAK_ALLIANCE",
    "ATTACK_CITY", "DEFEND_CITY",
    "INFILTRATE", "SPREAD_PROPAGANDA",
    "MOVE_TO",
    "NO_OP",
}

VALID_CITY_IDS = {"nexus", "ironhold", "freeport", "eden", "vault"}

VALID_AGENT_IDS = {
    "agent-0", "agent-1", "agent-2", "agent-3", "agent-4",
}

AGENT_ROSTER = {
    "agent-0": {"name": "The Grinder",   "color": 0xFF42A5F5},
    "agent-1": {"name": "The Shark",     "color": 0xFFEF5350},
    "agent-2": {"name": "The Diplomat",  "color": 0xFFAB47BC},
    "agent-3": {"name": "The Gambler",   "color": 0xFF66BB6A},
    "agent-4": {"name": "The Architect", "color": 0xFFFFA726},
}


# =============================================================================
#  REQUEST / RESPONSE MODELS
# =============================================================================

class AgentTurnRequest(BaseModel):
    agent_id: str
    personality_name: str
    personality_description: str
    priorities: str
    aggression: float
    risk_tolerance: float
    cooperation: float
    wallet: float
    allocated_prompts: int
    prompts_served: int
    in_debt: bool
    memory_summary: str
    world_state: dict
    economy_state: dict
    other_agents: list[dict]
    current_tick: int
    current_city: str = "unknown"
    all_agent_locations: dict = {}


class AgentAction(BaseModel):
    type: str
    target_id: str | None = None
    params: dict = {}
    reasoning: str = ""


class AgentTurnResponse(BaseModel):
    agent_id: str
    actions: list[AgentAction]
    internal_thought: str = ""


class AllianceRequest(BaseModel):
    agent_id: str
    personality_name: str
    priorities: str
    proposer_id: str
    proposer_name: str
    proposer_description: str
    alliance_terms: str
    memory_summary: str
    wallet: float
    proposer_wallet: float


class AllianceResponse(BaseModel):
    agent_id: str
    accept: bool
    reasoning: str = ""


# =============================================================================
#  PROMPT BUILDERS
# =============================================================================

def build_turn_prompt(req: AgentTurnRequest) -> str:

    cities_text = ""
    cities = req.world_state.get("cities", {})
    for city_id, city in cities.items():
        supply_contribution = city.get("supply_contribution", 0)
        demand_multiplier   = city.get("demand_multiplier", 1.0)
        cities_text += (
            f"  {city['name']} ({city_id}):\n"
            f"    Happiness: {city['happiness']:.1f}  Infrastructure: {city['infrastructure']:.1f}  "
            f"Defenses: {city['digital_defenses']:.1f}  Cohesion: {city['social_cohesion']:.1f}\n"
            f"    Employment: {city['employment_rate']:.1f}%  Treasury: ${city['treasury']:.1f}  "
            f"GDP/tick: {city['economic_output']:.2f}\n"
            f"    Supply contribution: {supply_contribution:.1f}  Demand multiplier: {demand_multiplier:.2f}x\n"
        )

    agents_text = ""
    for agent in req.other_agents:
        loc    = agent.get("location", "unknown")
        wallet = agent.get("wallet", 0)
        agents_text += (
            f"  {agent.get('name', agent['id'])} ({agent['id']}): "
            f"wallet=${wallet:.1f}  location={loc}  "
            f"in_debt={agent.get('in_debt', False)}\n"
        )

    market      = req.economy_state.get("market", {})
    world_gdp   = req.world_state.get("total_gdp", 0)
    demand_mult = req.world_state.get("avg_demand_multiplier", 1.0)
    economy_text = (
        f"  Prompt Price: ${market.get('price', '?')}  "
        f"Supply: {market.get('total_supply', '?')}  "
        f"Demand: {market.get('current_demand', '?')}\n"
        f"  Market Value: ${market.get('total_market_value', '?')}  "
        f"World GDP: {world_gdp:.1f}  "
        f"Avg Demand Multiplier: {demand_mult:.2f}x\n"
    )

    distance_warning = ""
    if req.current_city != "unknown":
        distance_warning = (
            f"\nNOTE: Acting on distant cities costs more. "
            f"Cost multiplier = 1.0 + (distance/20). Move near your targets to save money.\n"
        )

    valid_agents_str = ", ".join(sorted(VALID_AGENT_IDS - {req.agent_id}))

    return f"""You are "{req.personality_name}".
Description: {req.personality_description}
Your priorities: {req.priorities}
Aggression: {req.aggression:.1f}/1.0 | Risk Tolerance: {req.risk_tolerance:.1f}/1.0 | Cooperation: {req.cooperation:.1f}/1.0

=== YOUR STATUS ===
Wallet: ${req.wallet:.2f}
Current Location: {req.current_city}
Allocated Prompts: {req.allocated_prompts}  Prompts Served: {req.prompts_served}
In Debt: {req.in_debt}  Tick: {req.current_tick}

=== YOUR MEMORY ===
{req.memory_summary}

=== WORLD STATE ===
HOW STATS AFFECT THE MARKET:
- Infrastructure increases global prompt SUPPLY. Build it to expand the market.
- Happiness increases global DEMAND — but ONLY if Social Cohesion > 40.
- Social Cohesion < 40 INVERTS happiness. A happy city with low cohesion SUPPRESSES demand.
  Spread propaganda to tank a rival's demand even if their city looks happy.
- Defenses reduce attack damage by their percentage. 80 defenses = 20% damage lands.
  Also reduces INFILTRATE (50% effective) and PROPAGANDA (30% effective).
- High infrastructure + low employment = labor shortage = happiness penalty.

Cities:
{cities_text}{distance_warning}
=== ECONOMY ===
{economy_text}
=== OTHER AGENTS ===
{agents_text}
=== YOUR TASK ===
Choose 1-2 actions this tick. Stay true to your personality.
Learn from memory — stop repeating failures, double down on successes.

AVAILABLE ACTIONS:
- MOVE_TO: Travel to a city. Target: city_id. Cost $1/tile. No params.
- PLACE_BID: Bid for prompt allocation. Params: {{"price": <float>, "quantity": <int>}}
- PLACE_ASK: Sell prompts. Params: {{"price": <float>, "quantity": <int>}}
- BUILD_INFRASTRUCTURE: Boost city supply capacity. Target: city_id. Params: {{"amount": <1-20>}}
- DAMAGE_INFRASTRUCTURE: Sabotage city supply. Target: city_id. Params: {{"amount": <1-20>}}
- BOOST_HAPPINESS: Increase city demand. Target: city_id. Params: {{"amount": <1-15>}}
- DAMAGE_HAPPINESS: Reduce city demand. Target: city_id. Params: {{"amount": <1-15>}}
- ATTACK_CITY: Direct attack, blocked by defenses. Target: city_id. Params: {{"power": <5-30>}}
- DEFEND_CITY: Fortify defenses. Target: city_id. Params: {{"amount": <1-20>}}
- INFILTRATE: Covertly strip defenses. Target: city_id. Params: {{"amount": <1-10>}}
- SPREAD_PROPAGANDA: Lower cohesion to invert demand. Target: city_id. Params: {{"amount": <1-10>}}
- DRAIN_TREASURY: Steal city funds. Target: city_id. Params: {{"amount": <float>}}
- INJECT_CAPITAL: Fund a city. Target: city_id. Params: {{"amount": <float>}}
- FORM_ALLIANCE: Propose alliance. Target: agent_id. Params: {{"terms": "<string>"}}
- BREAK_ALLIANCE: End alliance. Target: agent_id.
- NO_OP: Skip this tick.

Valid city IDs: nexus, ironhold, freeport, eden, vault
Valid agent IDs: {valid_agents_str}

RESPONSE FORMAT: Return ONLY a JSON object. Keep internal_thought VERY short (under 15 words). Put actions first.
{{"actions": [{{"type": "ACTION_NAME", "target_id": "city_or_agent_id", "params": {{}}, "reasoning": "why in under 10 words"}}], "internal_thought": "brief"}}

Example valid response:
{{"actions": [{{"type": "BUILD_INFRASTRUCTURE", "target_id": "nexus", "params": {{"amount": 10}}, "reasoning": "Strengthen my base"}}], "internal_thought": "Build first, profit later"}}"""


def build_alliance_prompt(req: AllianceRequest) -> str:
    return f"""You are "{req.personality_name}".
Your priorities: {req.priorities}
Your wallet: ${req.wallet:.2f}

=== YOUR MEMORY ===
{req.memory_summary}

=== ALLIANCE PROPOSAL ===
{req.proposer_name} ({req.proposer_id}) proposes an alliance.
Their wallet: ${req.proposer_wallet:.2f}
Their description: {req.proposer_description}
Their terms: "{req.alliance_terms}"

Should you accept? Does this help your priorities? Are they trustworthy? Could they betray you?

RESPONSE FORMAT: Return ONLY a JSON object like this (no extra text):
{{"accept": true, "reasoning": "short explanation"}}"""


# =============================================================================
#  GEMINI CALLER
# =============================================================================

async def call_gemini_json(prompt: str, retries: int = 2) -> dict:
    """Call Gemini and extract valid JSON from the response.

    Strategies in order:
    1. Strip markdown fences, direct json.loads
    2. Fix trailing commas
    3. Regex extract first { ... }
    4. Repair truncated JSON (close open braces/brackets)
    """
    last_error = None
    for attempt in range(retries):
        try:
            response = await asyncio.to_thread(
                model.generate_content,
                prompt,
                generation_config=genai.GenerationConfig(
                    temperature=0.7,
                    max_output_tokens=2048,
                ),
            )

            text = response.text.strip()

            # Strip markdown fences
            if text.startswith("```json"):
                text = text[7:]
            if text.startswith("```"):
                text = text[3:]
            if text.endswith("```"):
                text = text[:-3]
            text = text.strip()

            # Try direct parse
            try:
                return json.loads(text)
            except json.JSONDecodeError:
                pass

            # Fix trailing commas
            cleaned = re.sub(r',\s*([}\]])', r'\1', text)
            try:
                return json.loads(cleaned)
            except json.JSONDecodeError:
                pass

            # Regex: extract first { ... }
            match = re.search(r'\{[\s\S]*\}', cleaned)
            if match:
                try:
                    return json.loads(match.group(0))
                except json.JSONDecodeError:
                    pass

            # TRUNCATION REPAIR: text starts with { but has no closing }
            # Gemini ran out of tokens mid-JSON. Try to close it.
            if text.lstrip().startswith('{'):
                repaired = _repair_truncated_json(text)
                if repaired is not None:
                    print(f"[AI] Attempt {attempt + 1}: repaired truncated JSON")
                    return repaired

            last_error = ValueError("Could not parse response")
            print(f"[AI] Attempt {attempt + 1}: JSON parse failed")
            print(f"[AI] Raw text (first 300 chars): {text[:300]}")

        except Exception as e:
            last_error = e
            print(f"[AI] Attempt {attempt + 1} failed: {e}")

        if attempt < retries - 1:
            await asyncio.sleep(1.0)

    # All retries exhausted — safe fallback
    print(f"[AI] All {retries} attempts failed, returning NO_OP fallback")
    return {
        "internal_thought": f"AI parse failed after {retries} attempts: {last_error}",
        "actions": [{"type": "NO_OP", "reasoning": "AI response was unparseable"}]
    }


def _repair_truncated_json(text: str) -> dict | None:
    """Attempt to close truncated JSON by brute-force appending brackets.

    Gemini often truncates mid-string like:
      {"internal_thought": "blah blah blah...
    We try closing the string and all open containers.
    """
    # Common truncation suffixes to try, most specific first
    suffixes = [
        '"}]}',          # truncated inside reasoning string
        '"}]}}',
        '": ""}]}',      # truncated inside a key
        '"}]}',
        '"]}}',
        '"}}',
        '"}',
        ']}',
        '}',
        '": 5}]}',       # truncated inside params
        ': 5}]}',
        '", "reasoning": "truncated"}]}',
    ]

    # Also try: if we can find "actions" already, just close it
    text_clean = re.sub(r',\s*([}\]])', r'\1', text)

    for suffix in suffixes:
        candidate = text_clean + suffix
        try:
            result = json.loads(candidate)
            # Validate it has the structure we need
            if isinstance(result, dict) and "actions" in result:
                return result
            if isinstance(result, dict) and "internal_thought" in result:
                # Has thought but no actions — add default
                result.setdefault("actions", [{"type": "NO_OP", "reasoning": "truncated response"}])
                return result
        except json.JSONDecodeError:
            continue

    return None


# =============================================================================
#  VALIDATION
# =============================================================================

def validate_action(action_data: dict) -> AgentAction:
    action_type = action_data.get("type", "NO_OP").upper().strip()

    if action_type not in VALID_ACTIONS:
        print(f"[AI] Invalid action type '{action_type}', defaulting to NO_OP")
        action_type = "NO_OP"

    target_id = action_data.get("target_id")
    if target_id == "null" or target_id == "":
        target_id = None

    params    = action_data.get("params", {})
    reasoning = action_data.get("reasoning", "")

    city_actions = {
        "BUILD_INFRASTRUCTURE", "DAMAGE_INFRASTRUCTURE",
        "BOOST_HAPPINESS", "DAMAGE_HAPPINESS",
        "ATTACK_CITY", "DEFEND_CITY",
        "INFILTRATE", "SPREAD_PROPAGANDA",
        "DRAIN_TREASURY", "INJECT_CAPITAL",
        "MOVE_TO",
    }
    agent_actions = {"FORM_ALLIANCE", "BREAK_ALLIANCE"}

    if action_type in city_actions:
        if target_id not in VALID_CITY_IDS:
            print(f"[AI] Invalid city '{target_id}' for {action_type}, defaulting to NO_OP")
            action_type = "NO_OP"
            target_id   = None

    if action_type in agent_actions:
        if target_id not in VALID_AGENT_IDS:
            print(f"[AI] Invalid agent '{target_id}' for {action_type}, defaulting to NO_OP")
            action_type = "NO_OP"
            target_id   = None

    clamped_params = {}
    for key, value in params.items():
        if isinstance(value, (int, float)):
            clamped_params[key] = max(-1000.0, min(1000.0, float(value)))
        else:
            clamped_params[key] = value

    if action_type in ("PLACE_BID", "PLACE_ASK"):
        clamped_params["price"]    = max(0.01, min(100.0, float(clamped_params.get("price", 1.0))))
        clamped_params["quantity"] = max(1, min(500, int(clamped_params.get("quantity", 10))))

    if action_type in ("BUILD_INFRASTRUCTURE", "DAMAGE_INFRASTRUCTURE", "DEFEND_CITY"):
        clamped_params["amount"] = max(1.0, min(20.0, float(clamped_params.get("amount", 5.0))))

    if action_type in ("BOOST_HAPPINESS", "DAMAGE_HAPPINESS"):
        clamped_params["amount"] = max(1.0, min(15.0, float(clamped_params.get("amount", 5.0))))

    if action_type == "ATTACK_CITY":
        clamped_params["power"] = max(5.0, min(30.0, float(clamped_params.get("power", 10.0))))

    if action_type in ("INFILTRATE", "SPREAD_PROPAGANDA"):
        clamped_params["amount"] = max(1.0, min(10.0, float(clamped_params.get("amount", 3.0))))

    if action_type in ("DRAIN_TREASURY", "INJECT_CAPITAL"):
        clamped_params["amount"] = max(0.0, min(500.0, float(clamped_params.get("amount", 10.0))))

    return AgentAction(
        type=action_type,
        target_id=target_id,
        params=clamped_params,
        reasoning=reasoning[:500]
    )


# =============================================================================
#  ENDPOINTS
# =============================================================================

@app.post("/ai/turn", response_model=AgentTurnResponse)
async def agent_turn(req: AgentTurnRequest):
    prompt = build_turn_prompt(req)
    result = await call_gemini_json(prompt)

    raw_actions = result.get("actions", [])
    if not raw_actions:
        raw_actions = [{"type": "NO_OP", "reasoning": "No action decided."}]

    raw_actions       = raw_actions[:2]
    validated_actions = [validate_action(a) for a in raw_actions]
    internal_thought  = result.get("internal_thought", "")

    print(f"[AI] {req.agent_id} ({req.personality_name}) tick {req.current_tick} "
          f"@ {req.current_city}: {[a.type for a in validated_actions]}")

    return AgentTurnResponse(
        agent_id=req.agent_id,
        actions=validated_actions,
        internal_thought=internal_thought[:1000]
    )


@app.post("/ai/alliance", response_model=AllianceResponse)
async def alliance_decision(req: AllianceRequest):
    prompt = build_alliance_prompt(req)
    result = await call_gemini_json(prompt)

    accept    = bool(result.get("accept", False))
    reasoning = result.get("reasoning", "")

    print(f"[AI] Alliance: {req.proposer_id} -> {req.agent_id}: "
          f"{'ACCEPTED' if accept else 'REJECTED'}")

    return AllianceResponse(
        agent_id=req.agent_id,
        accept=accept,
        reasoning=reasoning[:500]
    )


@app.get("/health")
async def health():
    return {"ok": True}


@app.get("/roster")
async def roster():
    """Returns agent names and colors — Flutter can pull this once on startup."""
    return AGENT_ROSTER


# =============================================================================
#  NARRATOR — 6th AI agent, unbiased tick summarizer
# =============================================================================

class NarrationRequest(BaseModel):
    tick: int
    events: list[dict]
    world_summary: dict
    economy_summary: dict
    agent_thoughts: dict = {}


class NarrationResponse(BaseModel):
    tick: int
    narration: str


async def call_gemini_text(prompt: str, max_tokens: int = 1024) -> str:
    """Simple text call — no JSON needed, just plain English."""
    try:
        response = await asyncio.to_thread(
            model.generate_content,
            prompt,
            generation_config=genai.GenerationConfig(
                temperature=0.6,
                max_output_tokens=max_tokens,
            ),
        )
        return response.text.strip()
    except Exception as e:
        print(f"[AI-TEXT] Gemini call failed: {e}")
        return "The world turns. Events unfold beyond the narrator's sight."


def build_narration_prompt(req: NarrationRequest) -> str:
    # Agent ID to name mapping
    id_to_name = {
        "agent-0": "The Grinder", "agent-1": "The Shark",
        "agent-2": "The Diplomat", "agent-3": "The Gambler",
        "agent-4": "The Architect",
    }

    def replace_ids(text: str) -> str:
        for aid, name in id_to_name.items():
            text = text.replace(aid, name)
        return text

    # Summarize events into a compact list
    event_lines = []
    for evt in req.events[:15]:
        desc = evt.get("description", "")
        if desc:
            event_lines.append(f"- {replace_ids(desc)}")
    events_text = "\n".join(event_lines) if event_lines else "- No significant events."

    # Summarize city states
    cities_text = ""
    cities = req.world_summary.get("cities", {})
    for city_id, city in cities.items():
        name = city.get("name", city_id)
        happiness = city.get("happiness", "?")
        treasury = city.get("treasury", "?")
        cities_text += f"  {name}: happiness={happiness}, treasury=${treasury}\n"

    # Market
    market = req.economy_summary.get("market", {})
    price = market.get("price", "?")
    demand = market.get("current_demand", "?")
    supply = market.get("total_supply", "?")

    return f"""You are the Narrator of a city-building economic simulation. You are NOT a player — you are a neutral observer. Your job is to summarize what happened this tick in 2-3 short, engaging sentences that a viewer can quickly read.

AGENT NAME MAPPING (use ONLY these names, never invent new ones):
  agent-0 = The Grinder
  agent-1 = The Shark
  agent-2 = The Diplomat
  agent-3 = The Gambler
  agent-4 = The Architect

Rules:
- Be neutral and unbiased. Don't take sides.
- Use the EXACT agent names above. Replace any "agent-0" with "The Grinder", etc.
- NEVER invent names like "The Shadow", "The Saboteur", "The Disruptor" — these do not exist.
- Focus on the most impactful events — attacks, big trades, alliances, city collapses.
- If nothing interesting happened, say so briefly.
- Never give strategy advice. Just report what happened.
- Keep it under 50 words.

TICK {req.tick} EVENTS:
{events_text}

CITY STATUS:
{cities_text}
MARKET: Price=${price}, Demand={demand}, Supply={supply}

Write your summary now (2-3 sentences, under 50 words):"""


@app.post("/ai/narrate", response_model=NarrationResponse)
async def narrate_tick(req: NarrationRequest):
    prompt = build_narration_prompt(req)
    narration = await call_gemini_text(prompt, max_tokens=2048)

    # Clean up any quotes or extra formatting
    narration = narration.strip().strip('"').strip()
    if len(narration) > 500:
        narration = narration[:497] + "..."

    print(f"[NARRATOR] Tick {req.tick}: {narration[:80]}...")

    return NarrationResponse(tick=req.tick, narration=narration)


# =============================================================================
#  LEGACY ENDPOINTS
# =============================================================================

class ProposeRequest(BaseModel):
    agent_id: str
    personality: str
    priorities: str
    base_policy: str
    context_data: str


class RateRequest(BaseModel):
    agent_id: str
    personality: str
    priorities: str
    proposals: list[dict]
    own_agent_id: str


@app.post("/ai/propose")
async def propose_legacy(req: ProposeRequest):
    print(f"[AI] WARNING: Legacy /ai/propose called by {req.agent_id}")
    return {
        "agent_id": req.agent_id,
        "proposed_text": "Legacy endpoint - migrate to /ai/turn",
        "params": {"drift": 0, "volatility": 1, "liquidity": 1, "spread_bps": 10, "shock_prob": 0.05},
        "satisfaction": 0.5
    }


@app.post("/ai/rate")
async def rate_legacy(req: RateRequest):
    print(f"[AI] WARNING: Legacy /ai/rate called by {req.agent_id}")
    grades = {p["agent_id"]: 0.0 for p in req.proposals if p["agent_id"] != req.own_agent_id}
    return {"agent_id": req.agent_id, "grades": grades}


# =============================================================================
#  DEBATE SYSTEM — Post-game AI argumentation + thesis generation
# =============================================================================

class DebateOpeningRequest(BaseModel):
    agent_id: str
    personality_name: str
    personality_description: str
    priorities: str
    wallet_history: list[float]       # wallet at each tick
    final_wallet: float
    total_actions: int
    action_summary: dict              # {"BUILD": 12, "ATTACK": 3, ...}
    city_states: dict                 # final city stats
    all_agent_wallets: dict           # {"agent-0": 340.5, ...}
    event_highlights: list[str]       # key events involving this agent
    territory_counts: dict            # {"nexus": 28, ...}
    final_tick: int


class DebateRebuttalRequest(BaseModel):
    agent_id: str
    personality_name: str
    personality_description: str
    priorities: str
    own_opening: str
    other_openings: dict              # {"agent-1": "statement...", ...}
    final_wallet: float
    all_agent_wallets: dict


class DebateThesisRequest(BaseModel):
    all_openings: dict                # {"agent-0": "...", ...}
    all_rebuttals: dict               # {"agent-0": "...", ...}
    wallet_histories: dict            # {"agent-0": [100, 105, ...], ...}
    city_states: dict
    territory_counts: dict
    event_log_summary: str            # compressed event history
    final_tick: int


@app.post("/ai/debate/opening")
async def debate_opening(req: DebateOpeningRequest):
    """Each agent argues why their strategy was the best."""

    wallet_trend = "grew" if req.final_wallet > 100 else "shrank"
    top_actions = sorted(req.action_summary.items(), key=lambda x: -x[1])[:5]
    actions_text = ", ".join(f"{a}: {n}x" for a, n in top_actions)

    prompt = f"""You are "{req.personality_name}" in a post-game debate about an AI economic simulation.
Description: {req.personality_description}
Your priorities: {req.priorities}

THE GAME IS OVER AFTER {req.final_tick} TICKS. You must now defend your strategy.

=== YOUR PERFORMANCE ===
Final wallet: ${req.final_wallet:.2f} (started at $100, {wallet_trend})
Total actions taken: {req.total_actions}
Top actions: {actions_text}
Key moments: {'; '.join(req.event_highlights[:8])}

=== FINAL STANDINGS (wallets) ===
{chr(10).join(f"  {aid}: ${w:.2f}" for aid, w in sorted(req.all_agent_wallets.items(), key=lambda x: -x[1]))}

=== CITY STATES ===
{json.dumps(req.city_states, indent=2)[:600]}

=== TERRITORY ===
{json.dumps(req.territory_counts)}

=== YOUR TASK ===
Give a 60-80 word opening statement defending YOUR strategy. Argue why your approach was the smartest.
- Reference specific actions you took and their outcomes
- Connect your strategy to a real-world economic theory (Nash equilibrium, tragedy of the commons, Keynesian stimulus, predatory pricing, mercantilism, etc.)
- If you didn't win on wallet, argue why wallet isn't the only measure of success
- Stay in character. Be bold, be specific, be persuasive.

Respond with ONLY your statement as plain text. No JSON, no quotes, no preamble."""

    statement = await call_gemini_text(prompt, max_tokens=2048)
    statement = statement.strip().strip('"').strip()
    if len(statement) > 1500:
        statement = statement[:1497] + "..."

    print(f"[DEBATE] Opening from {req.agent_id}: {statement[:80]}...")
    return {"agent_id": req.agent_id, "statement": statement}


@app.post("/ai/debate/rebuttal")
async def debate_rebuttal(req: DebateRebuttalRequest):
    """Each agent responds to others' opening statements."""

    others_text = ""
    for aid, stmt in req.other_openings.items():
        name = AGENT_ROSTER.get(aid, {}).get("name", aid)
        others_text += f"\n{name} ({aid}): \"{stmt}\"\n"

    prompt = f"""You are "{req.personality_name}" in a heated post-game debate.
Your priorities: {req.priorities}
Your wallet: ${req.final_wallet:.2f}

YOUR OPENING STATEMENT WAS:
"{req.own_opening}"

THE OTHER AGENTS SAID:
{others_text}

=== FINAL STANDINGS ===
{chr(10).join(f"  {aid}: ${w:.2f}" for aid, w in sorted(req.all_agent_wallets.items(), key=lambda x: -x[1]))}

=== YOUR TASK ===
Write a 50-70 word rebuttal. You must:
- Call out the weakest argument from another agent BY NAME
- Defend your own strategy against any criticism
- Reference real economic theory to support your point
- Be sharp, specific, and stay in character

Respond with ONLY your rebuttal as plain text."""

    statement = await call_gemini_text(prompt, max_tokens=2048)
    statement = statement.strip().strip('"').strip()
    if len(statement) > 1500:
        statement = statement[:1497] + "..."

    print(f"[DEBATE] Rebuttal from {req.agent_id}: {statement[:80]}...")
    return {"agent_id": req.agent_id, "statement": statement}


@app.post("/ai/debate/thesis")
async def debate_thesis(req: DebateThesisRequest):
    """The narrator writes the final thesis based on the full debate + data."""

    openings_text = ""
    for aid, stmt in req.all_openings.items():
        name = AGENT_ROSTER.get(aid, {}).get("name", aid)
        openings_text += f"{name}: \"{stmt}\"\n"

    rebuttals_text = ""
    for aid, stmt in req.all_rebuttals.items():
        name = AGENT_ROSTER.get(aid, {}).get("name", aid)
        rebuttals_text += f"{name}: \"{stmt}\"\n"

    # Find winner by wallet
    wallets = {aid: hist[-1] if hist else 0 for aid, hist in req.wallet_histories.items()}
    winner_id = max(wallets, key=wallets.get)
    winner_name = AGENT_ROSTER.get(winner_id, {}).get("name", winner_id)

    prompt = f"""You are the Narrator of an AI economic simulation. The game has ended after {req.final_tick} ticks. Five AI agents with different personalities competed for limited resources across five cities. You must now write the definitive thesis on what happened and why.

=== FINAL WALLETS ===
{chr(10).join(f"  {AGENT_ROSTER.get(aid, {}).get('name', aid)}: ${w:.2f}" for aid, w in sorted(wallets.items(), key=lambda x: -x[1]))}

=== TERRITORY ===
{json.dumps(req.territory_counts)}

=== CITY STATES ===
{json.dumps(req.city_states, indent=2)[:800]}

=== EVENT SUMMARY ===
{req.event_log_summary[:600]}

=== OPENING STATEMENTS ===
{openings_text}

=== REBUTTALS ===
{rebuttals_text}

=== YOUR TASK ===
Write a 150-200 word thesis that:

1. DECLARES A WINNER and explains why they won (consider wallet, territory, city health, not just money)
2. Connects the winning strategy to a specific real-world economic theory (Nash equilibrium, Keynesian economics, predatory capitalism, tragedy of the commons, mercantilism, game theory, etc.)
3. Identifies the KEY TURNING POINT — which specific decision or tick changed the outcome
4. Evaluates whether COOPERATION or COMPETITION was more effective
5. Makes a broader claim about what this simulation reveals about economic behavior

Be analytical, cite the agents by name, reference their actual arguments from the debate.
Write as a neutral academic observer. This is your thesis — make it sharp.

Respond with ONLY the thesis as plain text."""

    thesis = await call_gemini_text(prompt, max_tokens=4096)
    thesis = thesis.strip().strip('"').strip()
    if len(thesis) > 3000:
        thesis = thesis[:2997] + "..."

    print(f"[DEBATE] THESIS: {thesis[:120]}...")
    return {
        "thesis": thesis,
        "winner_id": winner_id,
        "winner_name": winner_name,
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=9001)
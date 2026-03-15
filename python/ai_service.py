import os
import json
import asyncio

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import google.generativeai as genai

# ---- Config ----
load_dotenv()
API_KEY = os.environ.get("GOOGLE_GENAI_API_KEY")
if not API_KEY:
    raise RuntimeError("Set GOOGLE_GENAI_API_KEY environment variable")

genai.configure(api_key=API_KEY)
model = genai.GenerativeModel("models/gemini-2.5-flash")

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

Return ONLY valid JSON:
{{
  "internal_thought": "<private reasoning, 1-3 sentences>",
  "actions": [
    {{
      "type": "<ACTION_TYPE>",
      "target_id": "<city_id or agent_id or null>",
      "params": {{}},
      "reasoning": "<why, 1 sentence>"
    }}
  ]
}}"""


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

Return ONLY valid JSON:
{{
  "accept": <true or false>,
  "reasoning": "<1-2 sentences>"
}}"""


# =============================================================================
#  GEMINI CALLER
# =============================================================================

async def call_gemini_json(prompt: str, retries: int = 3) -> dict:
    for attempt in range(retries):
        try:
            response = await asyncio.to_thread(
                model.generate_content,
                prompt,
                generation_config=genai.GenerationConfig(
                    response_mime_type="application/json",
                    temperature=0.8,
                    max_output_tokens=2048,
                ),
            )
            text = response.text.strip()
            if text.startswith("```json"):
                text = text[7:]
            if text.startswith("```"):
                text = text[3:]
            if text.endswith("```"):
                text = text[:-3]
            return json.loads(text.strip())
        except (json.JSONDecodeError, Exception) as e:
            print(f"[AI] Attempt {attempt + 1} failed: {e}")
            if attempt == retries - 1:
                raise HTTPException(status_code=502, detail=f"Gemini parse failed: {e}")
            await asyncio.sleep(1)


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


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=9001)
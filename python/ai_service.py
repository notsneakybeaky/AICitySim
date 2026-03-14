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
    "NO_OP",
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
    world_state: dict        # Full world state (cities, regions, etc.)
    economy_state: dict      # Market price, demand, supply
    other_agents: list[dict] # Other agents' public info (id, wallet, alive, etc.)
    current_tick: int


class AgentAction(BaseModel):
    type: str
    target_id: str | None = None
    params: dict = {}
    reasoning: str = ""


class AgentTurnResponse(BaseModel):
    agent_id: str
    actions: list[AgentAction]
    internal_thought: str = ""  # The agent's private reasoning (for logging)


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
    # Build city summary
    cities_text = ""
    cities = req.world_state.get("cities", {})
    for city_id, city in cities.items():
        cities_text += (
            f"  {city['name']} ({city_id}):\n"
            f"    Population: {city['population']:,}\n"
            f"    Happiness: {city['happiness']:.1f}/100\n"
            f"    Infrastructure: {city['infrastructure']:.1f}/100\n"
            f"    Digital Defenses: {city['digital_defenses']:.1f}/100\n"
            f"    Social Cohesion: {city['social_cohesion']:.1f}/100\n"
            f"    Treasury: {city['treasury']:.2f}\n"
            f"    Economic Output: {city['economic_output']:.2f}\n"
            f"    Employment: {city['employment_rate']:.1f}%\n\n"
        )

    # Build other agents summary
    agents_text = ""
    for agent in req.other_agents:
        agents_text += (
            f"  {agent.get('name', agent['id'])} ({agent['id']}): "
            f"wallet={agent.get('wallet', '?')}, "
            f"alive={agent.get('alive', True)}\n"
        )

    # Build economy summary
    market = req.economy_state.get("market", {})
    economy_text = (
        f"  Prompt Price: {market.get('price', '?')}\n"
        f"  Total Supply: {market.get('total_supply', '?')}\n"
        f"  Current Demand: {market.get('current_demand', '?')}\n"
        f"  Total Market Value: {market.get('total_market_value', '?')}\n"
    )

    return f"""You are "{req.personality_name}".
Description: {req.personality_description}
Your priorities: {req.priorities}
Aggression: {req.aggression:.1f}/1.0 | Risk Tolerance: {req.risk_tolerance:.1f}/1.0 | Cooperation: {req.cooperation:.1f}/1.0

=== YOUR STATUS ===
Wallet: {req.wallet:.2f}
Allocated Prompts: {req.allocated_prompts}
Prompts Served: {req.prompts_served}
In Debt: {req.in_debt}
Current Tick: {req.current_tick}

=== YOUR MEMORY ===
{req.memory_summary}

=== WORLD STATE ===
Cities:
{cities_text}

=== ECONOMY ===
{economy_text}

=== OTHER AGENTS ===
{agents_text}

=== YOUR TASK ===
Based on your personality, your priorities, your memory of what worked and what failed,
and the current state of the world and economy, choose 1 to 3 actions to perform this tick.

You care about accumulating wealth and influence. Use your memory to avoid repeating
strategies that failed. Double down on strategies that succeeded.

Available actions:
- PLACE_BID: Bid for prompt allocation. Params: {{"price": <float>, "quantity": <int>}}
- PLACE_ASK: Offer prompts for sale. Params: {{"price": <float>, "quantity": <int>}}
- BUILD_INFRASTRUCTURE: Invest in a city. Target: city_id. Params: {{"amount": <float 1-20>}}
- DAMAGE_INFRASTRUCTURE: Sabotage a city. Target: city_id. Params: {{"amount": <float 1-20>}}
- BOOST_HAPPINESS: Run programs in a city. Target: city_id. Params: {{"amount": <float 1-15>}}
- DAMAGE_HAPPINESS: Cause unrest. Target: city_id. Params: {{"amount": <float 1-15>}}
- ATTACK_CITY: Direct attack. Target: city_id. Params: {{"power": <float 5-30>}}
- DEFEND_CITY: Fortify a city. Target: city_id. Params: {{"amount": <float 1-20>}}
- INFILTRATE: Lower defenses covertly. Target: city_id. Params: {{"amount": <float 1-10>}}
- SPREAD_PROPAGANDA: Lower social cohesion. Target: city_id. Params: {{"amount": <float 1-10>}}
- DRAIN_TREASURY: Steal from a city. Target: city_id. Params: {{"amount": <float>}}
- INJECT_CAPITAL: Fund a city. Target: city_id. Params: {{"amount": <float>}}
- FORM_ALLIANCE: Propose alliance. Target: other_agent_id. Params: {{"terms": "<string>"}}
- BREAK_ALLIANCE: End alliance. Target: other_agent_id.
- NO_OP: Do nothing this tick.

Return ONLY valid JSON in this exact format:
{{
  "internal_thought": "<your private reasoning about the situation, 1-3 sentences>",
  "actions": [
    {{
      "type": "<ACTION_TYPE>",
      "target_id": "<city_id or agent_id or null>",
      "params": {{}},
      "reasoning": "<why you chose this, 1 sentence>"
    }}
  ]
}}"""


def build_alliance_prompt(req: AllianceRequest) -> str:
    return f"""You are "{req.personality_name}".
Your priorities: {req.priorities}
Your wallet: {req.wallet:.2f}

=== YOUR MEMORY ===
{req.memory_summary}

=== ALLIANCE PROPOSAL ===
{req.proposer_name} ({req.proposer_id}) is proposing an alliance with you.
Their wallet: {req.proposer_wallet:.2f}
Their description: {req.proposer_description}
Their terms: "{req.alliance_terms}"

Should you accept this alliance? Consider:
- Will this help your priorities?
- Is this agent trustworthy based on your memory?
- Are they wealthy enough to be useful?
- Could they betray you?

Return ONLY valid JSON:
{{
  "accept": <true or false>,
  "reasoning": "<1-2 sentences explaining your decision>"
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
                    max_output_tokens=4096,
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
    """Validate and sanitize an action from the LLM."""
    action_type = action_data.get("type", "NO_OP").upper().strip()

    # Force valid action type
    if action_type not in VALID_ACTIONS:
        print(f"[AI] Invalid action type '{action_type}', defaulting to NO_OP")
        action_type = "NO_OP"

    target_id = action_data.get("target_id")
    params = action_data.get("params", {})
    reasoning = action_data.get("reasoning", "")

    # Clamp numeric params to prevent absurd values
    clamped_params = {}
    for key, value in params.items():
        if isinstance(value, (int, float)):
            # General clamp: nothing below -1000 or above 1000
            clamped_params[key] = max(-1000.0, min(1000.0, float(value)))
        else:
            clamped_params[key] = value

    # Specific clamps per action type
    if action_type == "PLACE_BID" or action_type == "PLACE_ASK":
        clamped_params["price"] = max(0.01, min(100.0, float(clamped_params.get("price", 1.0))))
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
        reasoning=reasoning[:500]  # Truncate reasoning
    )


# =============================================================================
#  ENDPOINTS
# =============================================================================

@app.post("/ai/turn", response_model=AgentTurnResponse)
async def agent_turn(req: AgentTurnRequest):
    """Main endpoint: agent observes the world and decides what to do."""
    prompt = build_turn_prompt(req)
    result = await call_gemini_json(prompt)

    # Parse and validate actions
    raw_actions = result.get("actions", [])
    if not raw_actions:
        # If the LLM returned nothing, default to NO_OP
        raw_actions = [{"type": "NO_OP", "reasoning": "No action decided."}]

    # Limit to 3 actions per turn
    raw_actions = raw_actions[:3]

    validated_actions = [validate_action(a) for a in raw_actions]

    internal_thought = result.get("internal_thought", "")

    print(f"[AI] {req.agent_id} ({req.personality_name}) tick {req.current_tick}: "
          f"{[a.type for a in validated_actions]}")

    return AgentTurnResponse(
        agent_id=req.agent_id,
        actions=validated_actions,
        internal_thought=internal_thought[:1000]
    )


@app.post("/ai/alliance", response_model=AllianceResponse)
async def alliance_decision(req: AllianceRequest):
    """Secondary endpoint: agent decides whether to accept an alliance."""
    prompt = build_alliance_prompt(req)
    result = await call_gemini_json(prompt)

    accept = bool(result.get("accept", False))
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


# =============================================================================
#  LEGACY ENDPOINTS (backward compatibility during migration)
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
    """Legacy endpoint. Will be removed after full migration."""
    print(f"[AI] WARNING: Legacy /ai/propose called by {req.agent_id}")
    return {
        "agent_id": req.agent_id,
        "proposed_text": "Legacy endpoint - migrate to /ai/turn",
        "params": {"drift": 0, "volatility": 1, "liquidity": 1, "spread_bps": 10, "shock_prob": 0.05},
        "satisfaction": 0.5
    }


@app.post("/ai/rate")
async def rate_legacy(req: RateRequest):
    """Legacy endpoint. Will be removed after full migration."""
    print(f"[AI] WARNING: Legacy /ai/rate called by {req.agent_id}")
    grades = {p["agent_id"]: 0.0 for p in req.proposals if p["agent_id"] != req.own_agent_id}
    return {"agent_id": req.agent_id, "grades": grades}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=9001)
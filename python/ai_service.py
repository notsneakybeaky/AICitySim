import os
import json
import asyncio

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import google.generativeai as genai

# ---- Config ----
load_dotenv() # <-- This automatically loads the variables from your .env file
API_KEY = os.environ.get("GOOGLE_GENAI_API_KEY")
if not API_KEY:
    raise RuntimeError("Set GOOGLE_GENAI_API_KEY environment variable")

genai.configure(api_key=API_KEY)
model = genai.GenerativeModel("models/gemini-2.5-flash")

app = FastAPI(title="AI Policy Service")


# ---- Request / Response Models ----

class ProposeRequest(BaseModel):
    agent_id: str
    personality: str
    priorities: str
    base_policy: str
    context_data: str


class ProposeResponse(BaseModel):
    agent_id: str
    proposed_text: str
    params: dict[str, float]
    satisfaction: float


class RateRequest(BaseModel):
    agent_id: str
    personality: str
    priorities: str
    proposals: list[dict]  # [{"agent_id": "...", "proposed_text": "..."}]
    own_agent_id: str


class RateResponse(BaseModel):
    agent_id: str
    grades: dict[str, float]  # {proposer_agent_id: score}


# ---- Prompts ----

def build_propose_prompt(req: ProposeRequest) -> str:
    return f"""You are a {req.personality}.
Your core priorities are: {req.priorities}.

You have received the following economic policy:
---
{req.base_policy}
---

Current market context: {req.context_data}

Your task:
1. Suggest specific changes to this policy that would benefit your interests.
2. Provide numeric parameters for the market regime you think this policy should create.

Return ONLY valid JSON in this exact format, no other text:
{{
  "proposed_text": "Your modified policy text here",
  "params": {{
    "drift": <float between -5 and 5>,
    "volatility": <float between 0.01 and 10>,
    "liquidity": <float between 0.01 and 5>,
    "spread_bps": <float between 1 and 500>,
    "shock_prob": <float between 0 and 1>
  }},
  "satisfaction": <float between 0 and 1, how happy you are with your own proposal>
}}"""


def build_rating_prompt(req: RateRequest) -> str:
    proposals_text = ""
    for i, p in enumerate(req.proposals):
        if p["agent_id"] == req.own_agent_id:
            continue
        proposals_text += f'PROPOSAL by {p["agent_id"]}:\n{p["proposed_text"]}\n\n'

    agent_ids = [p["agent_id"] for p in req.proposals if p["agent_id"] != req.own_agent_id]

    return f"""You are a {req.personality}.
Your core priorities are: {req.priorities}.

Rate each of the following policy proposals on a scale from -1.0 to 1.0
based on how well it serves YOUR interests.

-1.0 = terrible for you
 0.0 = neutral
 1.0 = perfect for you

{proposals_text}

Return ONLY valid JSON in this exact format, no other text:
{{
  {', '.join(f'"{aid}": <float between -1.0 and 1.0>' for aid in agent_ids)}
}}"""


# ---- Helper: call Gemini and parse JSON ----

async def call_gemini_json(prompt: str, retries: int = 3) -> dict:
    for attempt in range(retries):
        try:
            response = await asyncio.to_thread(
                model.generate_content,
                prompt,
                generation_config=genai.GenerationConfig(
                    response_mime_type="application/json",
                    temperature=0.7,
                    max_output_tokens=2048,
                ),
            )
            text = response.text.strip()
            # Strip markdown code blocks if Gemini wraps them
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


# ---- Endpoints ----

@app.post("/ai/propose", response_model=ProposeResponse)
async def propose(req: ProposeRequest):
    prompt = build_propose_prompt(req)
    result = await call_gemini_json(prompt)

    # Validate and clamp params
    params = result.get("params", {})
    clamped = {
        "drift":      max(-5.0,  min(5.0,   float(params.get("drift", 0)))),
        "volatility": max(0.01,  min(10.0,  float(params.get("volatility", 1)))),
        "liquidity":  max(0.01,  min(5.0,   float(params.get("liquidity", 1)))),
        "spread_bps": max(1.0,   min(500.0, float(params.get("spread_bps", 10)))),
        "shock_prob": max(0.0,   min(1.0,   float(params.get("shock_prob", 0.05)))),
    }

    return ProposeResponse(
        agent_id=req.agent_id,
        proposed_text=result.get("proposed_text", ""),
        params=clamped,
        satisfaction=max(0.0, min(1.0, float(result.get("satisfaction", 0.5)))),
    )


@app.post("/ai/rate", response_model=RateResponse)
async def rate(req: RateRequest):
    prompt = build_rating_prompt(req)
    result = await call_gemini_json(prompt)

    # Validate and clamp grades
    grades = {}
    for agent_id, score in result.items():
        if agent_id != req.own_agent_id:
            grades[agent_id] = max(-1.0, min(1.0, float(score)))

    return RateResponse(
        agent_id=req.agent_id,
        grades=grades,
    )


@app.get("/health")
async def health():
    return {"ok": True}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=9001)
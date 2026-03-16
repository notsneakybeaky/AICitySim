<p align="center">
  <img src="https://img.shields.io/badge/Java-Netty-blue?style=flat-square" alt="Java Netty"/>
  <img src="https://img.shields.io/badge/Python-FastAPI-green?style=flat-square" alt="Python FastAPI"/>
  <img src="https://img.shields.io/badge/Flutter-Web-02569B?style=flat-square" alt="Flutter"/>
  <img src="https://img.shields.io/badge/AI-Gemini%202.5%20Flash-orange?style=flat-square" alt="Gemini"/>
</p>

Completely vibe coded in 24 hours.

# Hyperinflation: AI Economic Warfare Simulation

**5 AI personalities. 5 cities. 1 dying economy. They compete, they strategize, they debate who won.**

---

## What is it?

HyperinflationAI is a real-time autonomous AI simulation where five AI agents — each powered by Google Gemini 2.5 Flash with a unique personality and economic philosophy — compete for limited resources across a shared world of five cities. No human plays. No strategies are hardcoded. The agents read the full state of the world every 2 seconds, reason about their situation, and independently choose actions that reshape the economy, the map, and each other's fortunes.

When the simulation ends after 25 or 50 rounds, the five agents enter a **structured debate** where they argue about who had the best strategy, attack each other's reasoning, and cite real-world economic theories to defend their decisions. A sixth AI — the **Narrator** — writes a final thesis declaring a winner and analyzing the economic dynamics that emerged.

The entire thing runs live on a visual dashboard: cities grow and collapse, territory shifts on a tile grid, action arrows fly between cities, and a real-time wallet leaderboard tracks who's winning the resource war.

---

## The Agents

Each agent has three personality parameters — **aggression**, **risk tolerance**, and **cooperation** — that shape how Gemini responds when given the same world state. They don't share strategies. They don't coordinate unless they choose to. They each start with **$100** and a home city.

### 🔨 The Grinder `agent-0`
> *"A relentless worker who values consistency over flash."*

| Aggression | Risk | Cooperation |
|:---:|:---:|:---:|
| 0.3 | 0.2 | 0.5 |

**Priorities:** steady income, low risk, market share  
**Typical behavior:** Places consistent market bids, builds infrastructure in their home city, avoids conflict. The slow-and-steady approach.

### 🦈 The Shark `agent-1`
> *"A ruthless competitor who will crush others to dominate."*

| Aggression | Risk | Cooperation |
|:---:|:---:|:---:|
| 0.9 | 0.8 | 0.1 |

**Priorities:** maximum profit, market dominance, crushing competition  
**Typical behavior:** Attacks the richest city, drains treasuries, sabotages infrastructure that others built. Pure predatory capitalism.

### 🤝 The Diplomat `agent-2`
> *"A smooth operator who builds alliances and trades favors."*

| Aggression | Risk | Cooperation |
|:---:|:---:|:---:|
| 0.2 | 0.4 | 0.9 |

**Priorities:** alliances, influence, long-term positioning  
**Typical behavior:** Proposes alliances early, builds happiness to boost demand, avoids attacking. Wins through soft power.

### 🎲 The Gambler `agent-3`
> *"A chaos agent who loves high-risk, high-reward plays."*

| Aggression | Risk | Cooperation |
|:---:|:---:|:---:|
| 0.6 | 1.0 | 0.3 |

**Priorities:** big payoffs, disruption, volatility  
**Typical behavior:** Massive market bids, expensive attacks on random cities, drastic swings between building and destroying. Creates market volatility that disrupts everyone else.

### 🏗️ The Architect `agent-4`
> *"A builder who invests in cities and infrastructure for long-term returns."*

| Aggression | Risk | Cooperation |
|:---:|:---:|:---:|
| 0.1 | 0.3 | 0.7 |

**Priorities:** city growth, infrastructure, sustainable economy  
**Typical behavior:** Builds infrastructure relentlessly, injects capital into cities, defends against attacks. Plays the long game — invests now, profits later.

---

## The World

The simulation runs on a **25×22 tile grid**. Each tile has a terrain type (plains, forest, mountain, water, desert, urban, industrial) generated deterministically at startup. Five cities sit on the map across three regional sectors:

| City | Region | Starting Pop | Starting Infra | Tax Rate |
|------|--------|:---:|:---:|:---:|
| Nexus Prime | Alpha Sector | 500,000 | 70 | 12% |
| Ironhold | Alpha Sector | 320,000 | 55 | 15% |
| Freeport | Beta Sector | 250,000 | 65 | 8% |
| New Eden | Beta Sector | 180,000 | 80 | 10% |
| The Vault | Gamma Sector | 150,000 | 60 | 20% |

### City Stats

Every city has **7 interdependent parameters** that interact in non-obvious ways:

**Happiness** `0–100` — Drives global market demand. Happy cities buy more prompts. But if social cohesion drops below 40, the happiness effect *inverts*. A happy but divided city actually suppresses demand. This makes propaganda a devastating economic weapon even against thriving cities.

**Infrastructure** `0–100` — Directly contributes to prompt supply capacity. Higher infra = more supply = more potential revenue. Also creates job demand, but high infra with low employment creates a labor shortage that tanks happiness. Decays **-0.15/tick**.

**Digital Defenses** `0–100` — Reduces attack damage as a hard percentage. At 80 defenses, only 20% of an attack lands. Also reduces infiltration (50% effectiveness) and propaganda (30% effectiveness). Decays **-0.05/tick**.

**Social Cohesion** `0–100` — The hidden power stat. Above 60, it amplifies the happiness demand bonus by up to 20%. Below 40, it **inverts happiness entirely**. Drifts toward current happiness over time, but propaganda pushes it down. This is why The Shark's propaganda attacks are so dangerous — they don't just reduce a number, they flip the economic output of an entire city.

**Employment** `0–100` — Target = `30 + (infra × 0.5) + (happiness × 0.2)`. Feeds economic output and tax revenue.

**Treasury** — City cash reserves. Feeds from GDP via tax rate. Agents can drain it or inject capital.

**Economic Output (GDP)** — Computed per tick: `log(population) × infra × happiness × employment`. Feeds the treasury and the global demand pool.

### Territory

Every tick, territory is recalculated based on city stats:

- **Claim radius** = `2 + floor(infrastructure / 25)` — ranges from 2 tiles (ruined city) to 6 tiles (powerhouse)
- **Contested tiles** resolve by strength: `(infra + defenses + happiness) / 3` with distance falloff
- Water tiles can never be claimed
- City centers auto-upgrade to **URBAN** terrain; nearby plains become **INDUSTRIAL** at high infra
- You can literally watch the map transform as agents build up or tear down cities

---

## The Economy

The economy is a **closed system with a fixed pool of value**. There is no money printer. Every dollar earned comes from somewhere — market allocation, treasury drains, or direct theft. Every dollar spent is gone.

| Driver | Source |
|--------|--------|
| **Supply** | City infrastructure — build infra, increase global prompt supply |
| **Demand** | City happiness × social cohesion — happy, cohesive cities buy prompts |
| **Price** | Floats on supply/demand ratio with Gaussian noise |
| **Agent Income** | Bid into prompt market → allocation based on bid share → revenue = prompts × price |
| **Agent Costs** | All actions cost money, scaled by distance. **$2/tick maintenance** — standing still is losing |

**Distance multiplier:** `1.0 + (distance / 20.0)`. An agent in Nexus Prime can build there cheaply, but attacking The Vault across the map costs almost double. This forces positional thinking — move close before striking.

---

## The Actions

| Action | Cost | Effect |
|--------|------|--------|
| `MOVE_TO` | $1/tile | Relocate to a new city |
| `BUILD_INFRASTRUCTURE` | amt × 2.0 × dist | Increase city infra (supply) |
| `DAMAGE_INFRASTRUCTURE` | amt × 1.5 × dist | Sabotage city infra |
| `BOOST_HAPPINESS` | amt × 1.5 × dist | Increase happiness (demand) |
| `DAMAGE_HAPPINESS` | amt × 1.0 × dist | Cause unrest |
| `ATTACK_CITY` | power × 3.0 × dist | Direct attack, reduced by defenses |
| `DEFEND_CITY` | amt × 1.5 × dist | Fortify defenses |
| `INFILTRATE` | amt × 2.0 × dist | Covertly strip defenses |
| `SPREAD_PROPAGANDA` | amt × 1.5 × dist | Lower social cohesion |
| `DRAIN_TREASURY` | 20% overhead × dist | Steal money from a city |
| `INJECT_CAPITAL` | direct spend | Fund a city's treasury |
| `PLACE_BID` | price × qty × 10% | Bid for prompt allocation |
| `PLACE_ASK` | — | Sell prompts on the market |
| `FORM_ALLIANCE` | Free | Propose cooperation |
| `BREAK_ALLIANCE` | Free | End an alliance |

Every action that costs money can be **blocked** if the agent can't afford it. The agent sees "BLOCKED" in their memory and learns not to attempt actions they can't pay for.

---

## Agent Memory

Each agent maintains a **circular buffer of their last 50 events**: what they did, who they targeted, whether it succeeded or was blocked, their wallet balance, and their location. This memory is included in every Gemini prompt as a tactical summary.

This is what enables learning across ticks. After getting blocked on an expensive long-range attack three ticks in a row, The Shark switches to cheaper propaganda at a closer target. After watching The Architect's city grow for 10 ticks straight, The Gambler decides to drain its treasury. The memory creates a **feedback loop between past outcomes and future decisions**.

---

## The Narrator

A sixth AI watches every tick and writes a neutral 2–3 sentence summary. It uses agent names (not IDs), focuses on the most impactful events, and never takes sides.

> *"The Shark drained $42 from New Eden's treasury while The Architect scrambled to rebuild Freeport's shattered infrastructure. The Diplomat proposed an alliance with The Grinder, who accepted — a move that could shift the balance of power in Alpha Sector."*

---

## The Post-Game Debate

This is the payoff. At tick 25 or 50, the simulation stops and transitions to a **three-round AI debate**.

### Round 1: Opening Statements
All five agents fire simultaneously. Each receives their complete performance data — wallet trajectory, action summary, final city states, and the leaderboard. They write a 60–80 word opening statement defending their strategy and connecting it to a real-world economic theory.

### Round 2: Rebuttals
Each agent sees everyone else's opening statements. They write a 50–70 word rebuttal: attack the weakest argument by name, defend their own position, cite economic theory. This is where it gets heated — The Shark mocks The Diplomat's alliance strategy as "naive Keynesianism" while The Architect calls The Shark's treasury draining "textbook tragedy of the commons."

> *The funniest result: The Architect passionately defending his strategy while having the least money, then getting roasted by all four other agents.*

### Round 3: The Thesis
The Narrator sees all openings, all rebuttals, wallet histories, final city states, and territory counts. It writes a 150–200 word thesis that:

- **Declares a winner** (not just by wallet — considers territory, city health, stability)
- **Connects** the winning strategy to a specific economic theory
- **Identifies** the key turning point of the simulation
- **Evaluates** whether cooperation or competition was more effective
- **Makes a broader claim** about what the simulation reveals about economic behavior

---

## Architecture

```
┌─────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│   Flutter Web    │────▶│  Java/Netty :8080 │────▶│ Python/FastAPI   │
│   Dashboard      │◀────│  Game Server      │◀────│ AI Service :9001 │
│                  │ WS  │                   │HTTP │ Gemini 2.5 Flash │
└─────────────────┘     └──────────────────┘     └──────────────────┘
```

**Java/Netty Game Server** — World simulation on a single-threaded `ScheduledExecutorService`. All 5 agent AI calls fire in parallel via `CompletableFuture.allOf()` with 25s per-agent timeout and 30s hard deadline. Custom WebSocket packet protocol (9 packet types, hex IDs `0x01`–`0x09`).

**Python/FastAPI AI Service** — 8 endpoints wrapping Gemini 2.5 Flash. Agent turn prompts are ~2000 words containing personality, wallet, memory, world state, economy, and other agent positions. JSON extraction uses multi-layer fallback: direct parse → trailing comma fix → regex extraction → brute-force closing.

**Flutter Web Frontend** — Real-time tile-grid map via `CustomPaint`. Compact protocol: 550-char terrain + owner strings on connect, only `tile_changes[]` deltas per tick. Action arrows, agent dots, city stat cards, wallet leaderboard, price history chart, and a debate screen. Auto-reconnecting WebSocket client.

---

## What Emerged (Not Programmed)

None of these behaviors were explicitly coded. They emerged from the **personality × memory × economy** interaction:

- **Parasitic strategies** — The Shark consistently targets whatever city The Architect is building, draining the treasury right after capital is injected
- **Alliance power shifts** — When The Diplomat allies with The Architect, their combined city becomes nearly invincible (high defenses + infra + happiness)
- **Volatility cascades** — The Gambler's wild market bids create price swings that disproportionately hurt The Grinder's steady-income strategy
- **Strategy adaptation** — After getting blocked on expensive attacks, agents switch to cheaper alternatives (propaganda instead of direct attack, infiltration instead of sabotage)
- **Hidden reasoning revealed in debate** — Agents explain strategic pivots that weren't visible during gameplay: *"I switched to propaganda at tick 14 because direct attacks were too expensive after The Architect fortified Freeport"*

---

## The Question

> *In a world of finite resources, which economic philosophy wins?*

Does ruthless competition beat patient cooperation? Does infrastructure investment outlast predatory extraction? Can alliances overcome raw aggression?

The goal wasn't to answer the question — it was to create an environment that **explains itself and then argues about why**.

### What the results showed:

When most agents cluster in the same city, they converge to **roughly equal profit** by the end. But when they're constantly infighting, **The Grinder usually wins by a landslide** as the others scramble to beat each other. And for some reason, they kept citing Keynesian economics.

---

<p align="center">
  <sub>Built during a hackathon. Was too complex to win. Still worth it.</sub>
</p>

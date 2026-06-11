Perfect timing to do this. You’ve locked enough laws that planning the code now will save you from rewrites later.

I’ll keep this technical, concrete, and non-tutorial-ish, as you asked earlier.
No code yet — just structure, responsibilities, and sequencing.

⸻

0. What Kernel 0.1 is (and is not)

Before planning code, we must be brutally clear:

Kernel 0.1 IS
•	A deterministic signal-propagation engine
•	With a fixed node substrate
•	Executing connection-based genomes
•	In discrete ticks
•	For one or many units
•	Location-free
•	Energy intake = ❌ not yet
•	Environment semantics = ❌ not yet

Kernel 0.1 IS NOT
•	A physics engine
•	A simulation of space
•	A life simulator
•	A gameplay system
•	A GUI concern

Think of it as:

A tiny virtual machine for evolving signal graphs.

That framing helps a lot.

⸻

1. Core architectural decision (already implicit)

You are building a data-oriented kernel, not an OO organism model.

So the kernel should be structured around:
•	flat arrays
•	indices
•	phases
•	explicit memory

Not “objects talking to each other”.

This aligns with:
•	performance
•	determinism
•	future parallelization
•	mutation friendliness

Good instinct so far.

⸻

2. Minimal module breakdown (Kernel 0.1)

This is the smallest clean cut that won’t paint you into a corner.

1️⃣ Kernel

Responsibility
•	Owns the tick loop
•	Enforces execution order
•	Coordinates buffers

Think: scheduler, not logic.

Knows
•	how many units exist
•	when a tick starts/ends

Does NOT know
•	what nodes mean
•	how genomes mutate
•	what actions do

⸻

2️⃣ Genome

Responsibility
•	Stores raw genes (32-bit ints)
•	Decodes genes into connections
•	Applies modulo rules
•	Maps int16 → weight

Knows
•	bit layout
•	node ID spaces
•	weight scaling constant

Does NOT know
•	signal values
•	ticks
•	energy
•	nodes

This must be pure & deterministic.

⸻

3️⃣ NodeSubstrate

Responsibility
•	Defines available nodes
•	Defines node execution rules

This is static, global, immutable.

You can model it as:
•	enums + switch
•	tables of function pointers
•	strategy map

But conceptually it is:

“The laws of signal transformation.”

⸻

4️⃣ UnitState

This is critical.

Responsibility
•	Holds all per-unit mutable state

At minimum:
•	node outputs (double[])
•	node accumulators (double[])
•	delay memory (double[])
•	genome reference

This is where double buffering lives.

⸻

5️⃣ ExecutionContext

(Optional but very clean)

Responsibility
•	Temporary per-tick scratch space
•	Counters for activity
•	Flags for dormancy

This avoids polluting UnitState.

⸻

3. Data layout (important)

This is where most people screw up later. You won’t if you decide now.

Per unit, you want:

UnitState
├── genome : int[]          // raw genes
├── nodeOutputPrev : double[]
├── nodeOutputNext : double[]
├── nodeAccumulator : double[]
├── delayMemory : double[]

Why this matters:
•	No object graphs
•	No pointers between nodes
•	No dynamic allocation per tick
•	Easy reset / copy / snapshot

You already leaned toward this — good.

⸻

4. Execution phases (kernel tick)

This should map 1:1 with your MD spec.

Tick pipeline (locked)
1.	Clear accumulators
2.	Propagate connections
3.	Evaluate nodes
4.	Swap buffers

No shortcuts.
No hidden work.

If you later add:
•	energy
•	decay
•	environment

They become additional phases, not modifications.

⸻

5. Where complexity lives (important)

Concept	Where it lives
Weight scaling	Genome decoding
Invalid connections	Genome decoding or propagation
Clamp behavior	NodeSubstrate
Delay memory	UnitState
Dormancy detection	Kernel / ExecutionContext
Structural decay	Kernel (later)
Energy	Kernel (later)

This separation keeps the kernel clean.

⸻

6. How many units to support in 0.1?

Even if you run only one, design for many.

Why:
•	data layout changes are painful later
•	single-unit kernels accidentally encode assumptions

So Kernel should naturally loop over:

for each unit:
execute tick

Even if N = 1.

⸻

7. Determinism rules (non-negotiable)

Lock these now in your head:
•	No random calls inside Kernel
•	No random calls inside Node execution
•	RAND node pulls from a seeded RNG owned by UnitState or Kernel
•	Order of evaluation is fixed and documented
•	Floating-point operations are consistent

This will matter a lot later.

⸻

8. What you should NOT code yet

Very important restraint here.

Do NOT implement yet:
•	mutation
•	reproduction
•	energy
•	environment
•	spatial movement
•	selection

Kernel 0.1 should be:

“Can I take a genome and evolve signal over time exactly as specified?”

That’s the test.

⸻

9. First concrete coding milestone (not implementation yet)

Your first real milestone should be:

“Given the vertical slice genome, print the node outputs for 10 ticks and verify they match expectation.”

No GUI.
No life.
No evolution.

Just numbers.

If you can do that, everything else becomes additive.

⸻

10. Suggested order of implementation (planning-level)

Not code, just order:
1.	Define node IDs & counts (constants)
2.	Define Genome bit decoding (pure functions)
3.	Define UnitState data layout
4.	Implement connection propagation
5.	Implement node execution switch
6.	Implement tick loop
7.	Verify vertical slice numerically

Only after that:
•	mutations
•	multiple units
•	energy
•	environment

⸻

11. One sentence to keep you honest

This is worth keeping in mind while coding:

If a behavior feels “obvious” while coding, you’re probably smuggling semantics.

Kernel 0.1 should feel a bit boring.
That’s how you know it’s right.

⸻

If you want, next we can:
•	sketch exact Java class boundaries
•	define arrays sizes & indices
•	or write kernel tick pseudocode that mirrors the MD spec line-by-line

Tell me which one you want next.
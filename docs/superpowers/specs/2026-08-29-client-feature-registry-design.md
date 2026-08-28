# Client Feature Registry for the Local LLM Helper

## Goal

Give the local LLM a reliable, machine-readable description of the user-facing capabilities of the Nurgling/Lanfir client. The LLM must be able to identify which bot, automation, hotkey, context action, QoL option, overlay, window, or integration is relevant to a player's request without receiving the entire catalogue on every turn.

This design covers capability discovery, metadata ownership, deterministic search, per-session availability, and the `AVAILABLE_CLIENT_FEATURES` prompt format. It does not implement observation of player behaviour, unsolicited suggestions, suggestion UI, or new execution permissions.

## Current inventory

The inventory below was verified against `codex/local-llm` on 2026-08-29.

- `BotRegistry` contains 193 live descriptors. Of these, 186 are shown in the bot menu and 107 can be used as scenario steps.
- Bot categories contain 20 resource bots, 25 production bots, 8 battle bots, 42 farming bots, 17 quality-farming bots, 50 utility bots, 19 building bots, and 12 tool/debug bots.
- `GobContextRegistry` contains 23 object-context actions.
- `TileContextRegistry` contains 2 tile-context actions.
- `src/nurgling` contains 39 `KeyBinding.get(...)` declaration sites representing 37 unique binding IDs. The duplicate declarations are the shared craft-one and craft-all bindings.
- `NSettingsWindow` exposes 35 settings panels in 4 categories.
- The settings packages contain 171 static checkbox-construction sites. This is an exposure-point count, not a count of independent features: several controls configure one feature, and several features are also represented by bot or context-action entries.

The code therefore already exposes at least 255 directly identifiable registry/hotkey entries and 426 total exposure points when settings controls are included. There is no honest deduplicated feature total yet because context actions, hotkeys, and QoL controls do not consistently have canonical feature IDs. Producing that exact total is an output of the registry migration rather than an input guessed from source layout.

User-facing capability families found during the audit are:

- bots for resources, production, combat, farming, livestock, utilities, building, and diagnostics;
- scenario automation, simple routes, quick actions, auto-selection, craft presets, and equipment presets;
- gob and tile context actions;
- configurable hotkeys and toolbelt actions;
- Areas, navigation, world blueprints, Base Planner, and map tools;
- visual overlays, markers, object hiding, object scaling, inventory overlays, and combat HUD options;
- multi-session/account controls;
- container database, map sharing/synchronisation, cookbook, and automapper integrations;
- Discord, starvation, parasite, auto-logout, and other alerts;
- general QoL behaviours such as auto-drink, movement preferences, login actions, and flower-menu automation.

## Design choice

Use a hybrid, contribution-based runtime catalogue.

Existing registries remain authoritative for facts they already own. Explicit semantic metadata is added only where it cannot be derived reliably. Adapters convert each existing registry into feature contributions, and `ClientFeatureRegistry` merges contributions that share a canonical ID.

This is preferred over a hand-maintained JSON catalogue because JSON would duplicate class, menu, hotkey, and config information. It is preferred over classpath annotation scanning because explicit provider wiring is deterministic, testable, and does not depend on reflection or package loading order.

## Package and components

New catalogue code belongs under `src/nurgling/agent/features/` and has no dependency on Haven widgets except in the per-session availability adapter.

```text
Existing registries and feature definitions
    BotRegistry
    GobContextRegistry / TileContextRegistry
    custom KeyBinding definitions
    FeatureSettingSpec definitions
    subsystem providers
              |
              v
       ClientFeatureProvider[]
              |
              v
       FeatureContribution merger
              |
              v
       immutable ClientFeatureRegistry
          |                    |
          v                    v
  FeatureSearchIndex   FeatureAvailabilityResolver
          |                    |
          +----------+---------+
                     v
              FeatureMatch list
                     |
                     v
       ClientFeatureContextFormatter
                     |
                     v
        AVAILABLE_CLIENT_FEATURES
```

The principal types are:

- `ClientFeature`: the immutable, merged capability description.
- `FeatureMetadata`: semantic fields owned by an implementation or descriptor.
- `FeatureActivation`: one way a user can reach the capability.
- `FeatureRequirement`: a prerequisite that can optionally be evaluated for a session.
- `FeatureExecution`: an optional reference to an existing agent tool; it grants no new authority.
- `FeatureContribution`: a partial description supplied by one source.
- `ClientFeatureProvider`: contributes definitions or activations from one existing source.
- `FeatureQuery` and `FeatureMatch`: deterministic search input and scored result.
- `FeatureAvailabilityContext`: session/profile-specific state used only while resolving a query.
- `ClientFeatureContextFormatter`: produces the bounded LLM projection.
- `AgentContextAssembler`: inserts transient feature and memory context into a request without storing it in conversation history.

## ClientFeature model

```java
public final class ClientFeature {
    public final String id;
    public final FeatureKind kind;
    public final LocalizedText name;
    public final LocalizedText description;
    public final List<String> effects;
    public final List<String> useWhen;
    public final List<FeatureActivation> activations;
    public final List<FeatureRequirement> requirements;
    public final List<String> limitations;
    public final Set<String> entityTags;
    public final Set<String> actionTags;
    public final Set<String> keywords;
    public final FeatureExecution execution;
    public final FeatureSourceRef source;
    public final boolean debugOnly;
}
```

`FeatureKind` contains `BOT`, `AUTOMATION`, `CONTEXT_ACTION`, `HOTKEY`, `QOL`, `OVERLAY`, `WINDOW`, and `INTEGRATION`.

`LocalizedText` carries a localisation key and a fallback value. The formatter resolves the current locale, while the search index includes the resolved text, English fallback, stable ID, and explicit Russian/English synonyms.

`FeatureActivation` is structured rather than a single string:

```java
public final class FeatureActivation {
    public final ActivationType type;
    public final String instruction;
    public final String menuPath;
    public final String bindingId;
    public final String defaultBinding;
    public final String currentBinding;
}
```

Activation types include `BOT_MENU`, `SCENARIO_STEP`, `GOB_CONTEXT`, `TILE_CONTEXT`, `HOTKEY`, `TOOLBELT`, `SETTINGS`, `WINDOW`, and `AUTOMATIC`.

`FeatureExecution` contains an existing tool name and target ID, for example `run_bot_action` and `fire`. It only tells the LLM that an already-authorised tool can execute the feature. A feature without execution metadata can still be recommended but cannot be run by the agent.

## Stable IDs and contribution merging

Canonical IDs use a namespace and are independent of class names:

```text
bot.fire
context.gob.fill_trough
context.tile.fill_water
hotkey.quick_action
qol.night_vision
automation.scenarios
window.base_planner
```

Existing bot IDs are preserved as source and execution IDs. For example, `bot.choper` may retain the historical misspelling for stability while `chopper` is indexed as a keyword. No existing scenario or tool payload changes as a consequence of adding the catalogue.

A feature may receive several contributions. The semantic owner supplies name, description, effects, use cases, requirements, limitations, and tags. Secondary sources add activation methods or availability facts. For example, a context action that starts an existing bot uses the bot's canonical feature ID and contributes `Ctrl+RMB on ...` rather than copying the bot description.

Merge rules are strict:

- scalar semantic fields have one declared owner;
- conflicting non-empty scalar values are validation errors;
- lists and sets are unioned in deterministic insertion order;
- duplicate activations are removed by their structured identity;
- execution references must resolve to an existing tool and source ID;
- all final records must satisfy the required-field validator.

These rules make accidental documentation divergence visible instead of silently selecting one copy.

## Metadata ownership by source

### Bots

`BotDescriptor` remains the source of bot ID, type, title, description, implementation class, icon, default settings, bot-menu visibility, scenario visibility, and stack behaviour.

It gains an optional `FeatureMetadata` value. The bot provider derives all existing facts and derives `BOT_MENU` and `SCENARIO_STEP` activations from the two existing flags. Only use cases, explicit effects, prerequisites, limitations, entity/action tags, synonyms, and execution safety need new declarations.

### Context actions

`GobContextAction` and `TileContextAction` gain `featureId()` and a metadata contribution method. Their current `appliesTo(...)`, `label()`, and `create(...)` methods remain the runtime source of applicability, visible label, and behaviour.

The registry adapter derives the activation type and fixed `Ctrl+RMB` instruction. Resource aliases and arbitrary applicability predicates cannot be converted reliably into user meaning, so entity tags and requirements remain explicit metadata beside the action implementation.

### Hotkeys

Custom bindings are created through a small `ClientKeyBindings.define(...)` factory. It delegates persistence and matching to the existing `KeyBinding`, registers the feature metadata, and exposes both default and current bindings in `FeatureActivation`.

Dynamic vanilla menu-resource bindings are not automatically classified as Nurgling features. Modified Haven-layer bindings can be migrated explicitly when their Nurgling ownership is known. The migration test reports unclassified custom binding IDs so new ones cannot disappear from the catalogue.

### QoL and settings

UI widget text is not a sufficient source of truth. It is only available after widget construction and carries no stable ID, requirements, or behavioural meaning.

Each user-facing setting therefore receives a `FeatureSettingSpec` near the panel or behaviour that owns it. The spec includes canonical feature ID, `NConfig.Key`, localisation keys, default value, metadata, and activation path. Simple panels should construct their checkbox from the spec. Specialised controls may continue using custom widgets but must reference the same spec and config key.

Controls that only tune another feature, such as an auto-drink percentage slider, contribute a requirement or parameter description to `qol.auto_drink`; they do not become independent top-level features.

### User-created presets and subsystem capabilities

Providers for scenarios, craft presets, equipment presets, quick-action presets, and routes expose the built-in capability as the primary feature. Saved preset names are optional session-specific variants and are only included when the query matches that capability. They do not become global immutable definitions.

Areas, Blueprints, Base Planner, database/map sync, cookbook, notifications, multi-session controls, and similar subsystems have small explicit providers at their existing manager or launcher boundary. Metadata is not collected by instantiating their windows.

## Global definitions and per-session availability

The immutable definition catalogue is process-global. Availability is not.

Each `AgentRuntime` belongs to one `NGameUI`, so it creates a `FeatureAvailabilityContext` containing only that session's UI, profile, config, current bindings, and saved presets. `ClientFeatureRegistry.search(query, availabilityContext)` resolves active/disabled/setup-required status for that inner game window.

The process-global `llama-server` therefore receives context selected for the requesting session. No current-character state, profile setting, preset, or availability result is stored in the global catalogue or reused by another account window.

Availability has three states:

- `AVAILABLE`: usable now or directly reachable;
- `REQUIRES_SETUP`: relevant, but a named configuration, area, preset, item, or permission is missing;
- `UNAVAILABLE`: cannot be used in the current session.

`REQUIRES_SETUP` results may still be returned because explaining how to enable a useful feature is part of the helper's job. `UNAVAILABLE` results receive a score penalty and are omitted unless the query explicitly names them.

## Search

The first version is deterministic lexical/tag search with no embeddings or vector database.

```java
public final class FeatureQuery {
    public final String text;
    public final Set<String> entityTags;
    public final Set<String> actionTags;
    public final Set<String> stateTags;
    public final Locale locale;
    public final int maxFeatures;
    public final int maxChars;
}

public List<FeatureMatch> search(
        FeatureQuery query,
        FeatureAvailabilityContext availability);
```

Version one populates only `text` from the latest user prompt. Entity, action, and state tags are extension points for later player-state analysis and remain empty in this scope.

The index normalises case, `ё/е`, punctuation, underscores, hyphens, and camel-case boundaries, then removes a small versioned list of Russian and English conversational stop words. It indexes canonical ID, localised names and descriptions, effects, use cases, entity/action tags, and explicit synonyms.

Ranking weights, from strongest to weakest, are:

1. exact canonical ID or full name;
2. exact keyword/synonym;
3. entity or action tag;
4. prefix and multi-token name match;
5. use-case match;
6. description/effect substring match.

All remaining meaningful query tokens must be accounted for somewhere in a candidate, but synonyms may satisfy them. Results use canonical ID as the final tie-breaker. `FeatureMatch` retains score and matched fields for tests and diagnostics.

Default limits for the 0.8B local model are 8 features and 6,000 UTF-16 characters. The formatter stops before the character budget rather than truncating JSON. An empty or irrelevant query produces no feature block; it never falls back to the whole catalogue.

The normalisation primitives in `SettingsSearch` may be extracted into a UI-independent helper, but the registry search has its own field weighting and synonym handling.

## LLM projection

The formatter emits a compact JSON object in one transient system message:

```json
{
  "schema": 1,
  "type": "AVAILABLE_CLIENT_FEATURES",
  "features": [
    {
      "id": "bot.fire",
      "kind": "BOT",
      "name": "Fire Starter",
      "description": "Зажигает и при необходимости дозаправляет печи и другие объекты",
      "use_when": ["нужно зажечь печь", "нужно запустить плавильню"],
      "activation": [{"type": "BOT_MENU", "path": "Боты > Утилиты"}],
      "requirements": ["подходящий объект должен быть доступен"],
      "limitations": [],
      "entities": ["oven", "smelter", "kiln", "fire"],
      "actions": ["refuel", "ignite"],
      "keywords": ["зажечь", "огонь", "растопить", "light", "ignite"],
      "availability": "AVAILABLE"
    }
  ]
}
```

Internal source references, search scores, implementation class names, and config storage details are not sent to the model.

`AgentContextAssembler` copies the conversation history, locates the latest user message, and inserts one transient system-context message immediately before it. The message may contain memory context and `AVAILABLE_CLIENT_FEATURES`, but it is never appended to persistent history. This prevents repeated catalogue blocks from accumulating across turns.

## Failure handling

- Invalid definitions are reported during catalogue construction with source and canonical ID.
- Development and tests fail on duplicate semantic ownership, missing mandatory fields, unresolved execution references, or unclassified registered sources.
- Production skips an invalid contribution, logs one concise diagnostic, and keeps unrelated features searchable.
- A provider failure does not make the LLM request fail; the context block is built from the remaining valid providers.
- Search and formatting are read-only and do not instantiate bots, open windows, mutate config, or execute actions.
- Failure to build any feature context sends the normal agent request without the optional block.

## Verification and catalogue freshness

Automated tests enforce source parity instead of hard-coded totals:

- every live `BotRegistry` descriptor produces exactly one bot contribution;
- bot menu/scenario activations equal the corresponding descriptor flags;
- every registered gob/tile context action has a valid canonical feature ID;
- every custom binding created through `ClientKeyBindings` appears in the catalogue with current/default activation values;
- a structural test rejects direct `KeyBinding.get(...)` calls in `src/nurgling` outside the factory after migration, preventing future custom bindings from bypassing registration;
- every migrated user-visible setting references one `FeatureSettingSpec`;
- contribution merging rejects silent scalar conflicts and produces deterministic list order;
- all canonical IDs are unique after merging;
- all execution references resolve to existing tools and source IDs;
- Russian/English synonyms and entity/action tags rank the expected features;
- debug features are excluded by default;
- result count and JSON character budgets are respected without malformed output;
- two session contexts with different bindings, config, or presets cannot affect each other's results;
- transient feature context is inserted before the latest user message and is not retained in history.

A deterministic full-catalogue JSON snapshot is generated as a test/build artifact for human review. It is not checked in as an editable source of truth.

During migration, an explicit temporary allow-list records unclassified legacy settings and bindings. Completion requires that allow-list to be empty; afterward a newly registered bot, context action, hotkey, or feature setting without metadata fails the parity test.

## Integration boundaries

The existing `ToolRouter.list_available_bots` can be backed by the bot projection of `ClientFeatureRegistry`, eliminating a second bot-description path. `run_bot_action` continues resolving the original `BotRegistry` ID and is not broadened to execute QoL or UI-only features.

`AgentRuntime.askModel()` asks `AgentContextAssembler` for transient context after resolving the latest user prompt and before calling `OpenAIChatClient`. Local/external LLM routing remains unchanged; both routes receive the same selected feature context.

No UI suggestion widget, player-action observer, periodic polling, embedding model, vector store, or new tool execution is part of this design.

## Implementation sequence

1. Add the immutable model, contribution merger, validator, search index, formatter, and unit tests.
2. Add the `BotRegistry` adapter and bot parity tests; enrich bot metadata without duplicating existing descriptor fields.
3. Add context-action metadata and merge alternate activations into canonical features.
4. Add the custom hotkey factory and migrate Nurgling-owned bindings.
5. Add `FeatureSettingSpec` and migrate QoL/settings panels and subsystem launchers until the unclassified allow-list is empty.
6. Add per-session availability providers for config, bindings, and user-created presets.
7. Add `AgentContextAssembler`, integrate the bounded block into `AgentRuntime`, and reuse the bot projection in `list_available_bots`.
8. Run the complete test suite, build the client, and inspect the generated catalogue artifact for coverage and duplicate concepts.

Implementation must follow test-driven development. Catalogue/search code remains independent of widget rendering so its behaviour can be covered by ordinary unit tests.

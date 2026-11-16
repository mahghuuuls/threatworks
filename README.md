# Threatworks

Threatworks adds configurable threat targeting to Minecraft 1.12.2. It lets modpacks give tanks ways to hold aggro through gear bonuses and taunts. A fresh configuration enables 22 ordinary vanilla hostile types and the qualified attacks of the Wither and Ender Dragon and automatically enables verified types from installed mods. The modded roster covers 217 types. The [compatibility roster](COMPATIBILITY.md) lists exact IDs and qualified combat states.

## Installation

Install Threatworks on the dedicated server for multiplayer or in the game instance for single-player. Players can join a dedicated server without Threatworks in their client. Optional companion mods have their own installation requirements. The release is a Forge 1.12.2 JAR targeting Cleanroom. Use a [Cleanroom runtime](https://cleanroommc.com/) whose built-in MixinBooter reports version 12.1 or later. The verified baseline was Cleanroom 0.7.14-alpha with MixinBooter 12.1.

## Configuration

Edit `config/threatworks.cfg` and restart the server or reopen the single-player world. In expanded mode, `enableVerifiedModdedMobs` enables the full verified roster. In both `expanded` and `expanded-all` coverage, use the `modtargeting` section to set a mod's registry namespace to `false` to disable Threatworks targeting for all its mobs. Find each namespace in the [compatibility roster](COMPATIBILITY.md). To exclude one entity type, add its exact ID to `mobBlacklist`.

```text
server {
    S:mobCoverage=expanded
    B:enableVerifiedModdedMobs=true
    S:mobBlacklist <
        minecraft:witch
     >

    modtargeting {
        B:ancientwarfarenpc=false
    }
}
```

Previously saved configurations keep their selected mobs after migration. In expanded mode, set `enableVerifiedModdedMobs=true` to adopt the full verified roster. With that switch off, the base Special Mobs zombie and verified IDs in `mobOptIn` remain enabled, subject to your mod switches and blacklist. A mod switch set to `false` overrides listed opt-ins for that mod. The exact blacklist applies in every coverage mode.

Existing configurations with an `enabledMobs` list and no coverage mode stay in legacy mode. To adopt both verified rosters, set `mobCoverage=expanded-all`. To manage them independently, use `mobCoverage=expanded` with `enableVerifiedVanillaMobs` and `enableVerifiedModdedMobs`. To opt in individual verified vanilla IDs, use `vanillaMobOptIn`.

The `itemThreatPointAssignments` list starts empty. An entry such as `minecraft:iron_chestplate=100` doubles the wearer's threat from direct hits while equipped if no other modifiers apply. A selector such as `minecraft:wool@14=-50` applies to a specific item variant. Negative points reduce future damage threat. A pack assignment takes precedence over the item's base provider. Equipped gear, compatible qualities, and active food, set and status effects can combine.

Operators can use `/threatworks inspect <single mob selector or UUID>` to view a mob's current target and up to 16 participant scores.

## Optional Integrations

| Mod | Verified baseline | Pack setup |
| --- | --- | --- |
| [Electroblob's Wizardry](https://www.curseforge.com/minecraft/mc-mods/electroblobs-wizardry) | 4.3.19 | Summoned zombies earn threat from melee hits and summoned skeletons earn it from arrows once tracking starts. |
| [Baubles](https://www.curseforge.com/minecraft/mc-mods/baubles) | 1.5.2 | Item assignments and providers also apply in equipped Baubles slots. |
| [Quality Tools](https://www.curseforge.com/minecraft/mc-mods/quality-tools) | 1.0.7 with Potion Core 1.9 | Add `{"name":"threatworks.threat_points","amount":100,"operation":0}` to a quality's `attributes` in the applicable slot. |
| [Just Another Food System](https://www.curseforge.com/minecraft/mc-mods/just-another-food-system) | 1.2.0, API 1.0 | Add `{"property":"threatworks:threat_points","operation":"flat","amount":100}` to a food definition's `modifiers`. |
| [Setworks](https://www.curseforge.com/minecraft/mc-mods/setworks) | 1.1.0 with CraftTweaker 4.1.20.718 for this script example | In a file under `scripts/` with `#loader setworks` on its first line, append `.effect("threatworks:threat_points", {"amount":"100"} as string[string])` to a bonus in a Setworks script. |
| [Statusworks](https://www.curseforge.com/minecraft/mc-mods/statusworks) | 1.3.0 | Configure signed, fixed or per-stack threat points on active statuses. See [Statusworks setup](#statusworks-setup). |

The listed integration versions are their adapter minimums. Companion versions identify the tested setup. The examples use positive amounts. Negative amounts reduce damage threat.

### Statusworks setup

Use API-compatible Statusworks 1.3.0 or later. In multiplayer, install matching Statusworks versions on the server and clients. A client can join without Threatworks. For script-defined statuses, use [CraftTweaker](https://www.curseforge.com/minecraft/mc-mods/crafttweaker). The tested CraftTweaker build was 4.1.20.718.

Put the integration in a separate `.zs` file under `scripts/`. The first line checks that both mods are installed. If the client lacks Threatworks, CraftTweaker skips this script on that client and Statusworks still syncs the status from the server. Use the version above on both sides. The example assigns +25 threat points per active stack, up to four stacks.

```zenscript
#modloaded statusworks threatworks
import mods.statusworks.Statuses;

Statuses.define("pack:threat_guard").limit(4).defaultDuration(200)
    .contributeType("threatworks:threat_points", "per_stack", 25.0)
    .register();
```

Use `"fixed"` for one value regardless of stack count. Negative values reduce threat generation. Changes to a status leave previously earned threat intact. Packs and addons decide how players receive the status. Java-defined statuses work without CraftTweaker. Threatworks registers the type on the server with compatible Statusworks installed. Add the contribution only where that integration is available. For example, use `Contribution.type("threatworks:threat_points", ValueForm.PER_STACK, 25.0)` in a Statusworks definition. Use `ValueForm.FIXED` for a fixed amount. [Statusworks script reference](https://github.com/mahghuuuls/statusworks/blob/4adae2ef35dc7e661ae8b5b5f4a28b3c47255c84/docs/crafttweaker.md#contributions) · [Statusworks Java API](https://github.com/mahghuuuls/statusworks/blob/4adae2ef35dc7e661ae8b5b5f4a28b3c47255c84/docs/java-api.md#your-own-contribution-types)

## Integration API

Use the Threatworks release JAR as a compile-time dependency for the APIs below. For Statusworks Java definitions, use the [Statusworks setup](#statusworks-setup).

### Reading threat

Call `ThreatworksMod.threatQueries().query(mob, participant)` from the logical server thread with two live entities in the same loaded world. The returned `ThreatQueries.View` is a read-only snapshot for that pair. An optional integration can check Forge's mod list and load its Threatworks API adapter when Threatworks is present. The integration controls its own client delivery.

- `INACTIVE` means a recognized vanilla type has no active encounter. Live AI availability is separate. A configured modded entity whose combat policy has yet to be observed can report `SUPPORT_UNKNOWN`. Unloaded, invalid and off-thread requests have specific unavailable states.
- For a valid live participant in a tracked encounter, `threat()` can return a known zero. Honor an empty value separately. Eligibility is the last usable observation, up to five server ticks old. Unknown means the query cannot confirm it from the currently observed combat path.
- Read `aggro()` with `focus()`. A Wither read refers to its center head. A Dragon result refers to a confirmed active ordinary strafe. A Guardian beam can publish its focus while the watched target and native attack target agree. Pending target writes, stale references and an unverified native path can leave focus unknown.
- Read `tauntActive()` and `tauntTicks()` for the encounter timer. `tauntForcingFocus()` separately indicates its effect on the confirmed combat path. A native attack can retain focus while the taunt timer continues.

- [Threat actions](src/main/java/com/mahghuuuls/threatworks/api/ThreatActions.java) are available from `ThreatworksMod.threatActions()`. Call on the server thread to grant threat in an active encounter or taunt a supported mob on behalf of an eligible participant. Check for `ThreatActions.Result.APPLIED` before treating the action as successful. Integrations define their own costs and cooldowns. The internal mob policies do not currently expose an entity-adapter registration API.
- [Item threat point providers](src/main/java/com/mahghuuuls/threatworks/api/ItemThreatPointProvider.java) supply signed points for equipped items. Register a provider with `ThreatworksMod.registerItemThreatPoints(item, provider)` during Forge initialization or post-initialization.
- [Summon threat credit](src/main/java/com/mahghuuuls/threatworks/api/SummonThreatCreditEvent.java) lets a server event listener call `creditOwner()` to redirect a supported summon hit in an active encounter to an eligible owner.

## Project links

[Mod page copy](MOD-PAGE.md) | [Changelog](CHANGELOG.md) | [MIT License](LICENSE) | [Third-party notices](THIRD-PARTY-NOTICES.md)

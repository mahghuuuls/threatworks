Threatworks gives supported hostile mobs a threat system inspired by classic World of Warcraft and its early expansions. In a group fight, a tank can hold a mob's attention while other players deal damage.

<span style="color:#d6a100">**AI usage disclaimer.** This mod was developed with AI-agent assistance using [this agent workflow](https://github.com/mahghuuuls/minecraft-1.12.2-mod-agent-workflow). The project owner reviewed the work during development.</span>

**Installation.** Install Threatworks on the server for multiplayer or in your game for single-player. Compatible multiplayer clients can join without Threatworks. Requires Cleanroom 1.12.2 with built-in MixinBooter 12.1 or later. [Cleanroom](https://cleanroommc.com/)

## What are threat and aggro?

**Threat** is a score each fighter builds with an individual mob. **Aggro** means you're the mob's current target, whether it's attacking you or chasing you.

Regular hits build threat with the default settings. Tracking starts when a player's hit removes health or a player lands a valid taunt. Each point of health removed gives one point of base threat to the attacker. A fully absorbed hit gives zero threat and keeps tracking off if it has yet to start. If a player opens tracking with a hit while the mob is already fighting someone it can still attack or pursue, that fighter gets 10 starting threat by default and keeps aggro through the hit.

## Tank and damage builds

Pack authors can assign threat bonuses or reductions to gear. A tank can wear gear that earns more threat from each hit to hold aggro. A damage dealer can wear gear that reduces the threat their hits generate, helping the tank keep aggro while they deal damage.

Threatworks supplies the taunt mechanic. Addons provide the abilities, spells, or items players use to trigger it. A successful taunt briefly draws a supported mob's aggro.

## How distance changes aggro

With the default settings, an eligible challenger normally needs **more than 20% above the current target's threat** to take aggro. The requirement rises only when the mob can attack its current target from where it stands and would have to move closer to attack the challenger. When that chase rule applies, use the straight-line distance from the mob to the challenger.

- **8 blocks or closer.** More than 20% extra threat.
- **Between 8 and 16 blocks.** The required lead rises steadily. At 12 blocks, it's more than 30%.
- **16 blocks or farther.** More than 40% extra threat.

If the tank has **100 threat** and you have **125**, you can pull aggro once the mob can attack you from where it stands. If it can still attack the tank but would have to move to attack you from 12 blocks away, you need more than 130 threat. Moving closer can make the mob switch without another hit. Distance changes how much threat you need to pull aggro. Your earned score stays the same as you move.

The ordinary 20% lead also applies if the mob would have to chase both fighters. A skeleton that can already shoot you uses the ordinary lead even if you're far away. A mob can only switch to someone it can attack or pursue. A mob that needs sight can keep its current target briefly when loss of sight is the only reason the target would become ineligible. If its current target dies, it can switch to a reachable fighter with a lower score.

## Which mobs use threat?

Fresh configurations enable threat for 22 ordinary vanilla hostile types. A Creeper can switch targets until its fuse commits. Guardians keep their target through a committed beam. Evokers and independent Vexes can switch between attacks, and summoned Vexes can still follow their Evoker. Zombie Pigmen and Endermen keep vanilla targeting.

- **Wither.** The center head follows threat and can be taunted. The side heads pick their own targets.
- **Ender Dragon.** Threat helps choose the target of a new ordinary strafing run. During the Dragon's circling phase, breaking an End Crystal can draw its native retaliation. The Dragon ignores taunts.

## Modded mob compatibility

Threatworks helps these mobs choose who to attack. The mobs' own AI handles movement, attacks, and special moves. Defiled Slimes share targeting rules with vanilla slimes, with a check for more room overhead along their route. The Foliaath uses dedicated checks for when it can bite and which targets it can reach.

Fresh configurations enable threat for 217 supported mob types when their mods are installed. Each entry covers specific creatures and combat states. The linked roster lists exact entity IDs, tested versions, and limits. [Compatibility roster](https://github.com/mahghuuuls/threatworks/blob/main/COMPATIBILITY.md)

- **Ancient Warfare 2: Tweaked.** Hostile faction archers, soldiers, and leaders use threat within their native weapon and home limits. [Ancient Warfare 2: Tweaked](https://www.curseforge.com/minecraft/mc-mods/ancient-warfare-2-tweaked)
- **BetterEndForge Backport.** End Slime hopping and contact attacks. [BetterEndForge Backport](https://www.curseforge.com/minecraft/mc-mods/betterendforge-backport)
- **Charm.** Spectre pursuit and melee in the dark. [Charm](https://www.curseforge.com/minecraft/mc-mods/charm)
- **Chocolate Quest Repoured.** Close melee for selected hostile mobs when unmounted and without a leader. [Chocolate Quest Repoured](https://www.curseforge.com/minecraft/mc-mods/cqrepoured)
- **Deeper Depths.** Bogged bow attacks, including poison arrows. [Deeper Depths](https://www.curseforge.com/minecraft/mc-mods/deeper-depths)
- **Defiled Lands.** Host, Scuttler, and Shambler melee, including Twisted Shamblers, plus Defiled Slime contact attacks. [Defiled Lands](https://www.curseforge.com/minecraft/mc-mods/defiled-lands)
- **Dungeon Mobs Reborn.** Selected melee mobs, including Ghouls, Ghosts, Revenants, Hook Horrors, and Umber Hulks. Use an enchanted weapon for ordinary melee against Ghosts. [Dungeon Mobs Reborn](https://www.curseforge.com/minecraft/mc-mods/dungeon-mobs-reborn)
- **Eerie Entities.** Cursed Armor and Nether Knight ground melee. [Eerie Entities](https://www.curseforge.com/minecraft/mc-mods/eerie-entities)
- **Fish's Undead Rising.** Selected ground melee and aerial charges. Committed charges and casts follow their native targeting. [Fish's Undead Rising](https://www.curseforge.com/minecraft/mc-mods/fishs-undead-rising)
- **Grimoire of Gaia 3.** Selected ground melee, bows, fireballs, magic projectiles, and poison splash attacks. [Grimoire of Gaia 3](https://www.curseforge.com/minecraft/mc-mods/grimoire-of-gaia)
- **Ice and Fire: RotN Edition.** Dread Beast, Ghoul, Knight, Scuttler, and Thrall attacks after emergence. [Ice and Fire: RotN Edition](https://www.curseforge.com/minecraft/mc-mods/ice-and-fire-rotn-edition)
- **Illagers+.** Archer bow attacks and Furantur nighttime melee. [Illagers+](https://www.curseforge.com/minecraft/mc-mods/illagers)
- **Magma Monsters.** Magma Monster and Grunt melee while cooled. Their molten phase uses native targeting. [Magma Monsters](https://www.curseforge.com/minecraft/mc-mods/magma-monsters)
- **Mo' Creatures Extended.** Selected scorpions, Silver Skeleton melee, and one wild hostile ground form of the HorseMob. [Mo' Creatures Extended](https://www.curseforge.com/minecraft/mc-mods/mo-creatures-extended)
- **Mowzie's Mobs.** Foliaath bites while the plant is active and its target is within reach. [Mowzie's Mobs](https://www.curseforge.com/minecraft/mc-mods/mowzies-mobs)
- **Natura Legacy.** Heatscar Spider melee on the ground and Nitro Creeper pursuit before its fuse commits. [Natura Legacy](https://www.curseforge.com/minecraft/mc-mods/natura-legacy)
- **Project: Vibrant Journeys.** Banshee and Shade ground melee at night. [Project: Vibrant Journeys](https://www.curseforge.com/minecraft/mc-mods/project-vibrant-journeys)
- **Quark: RotN Edition.** Ashen archery, Forgotten bow and sword combat, and Dweller, Pirate, and Wraith melee. [Quark: RotN Edition](https://www.curseforge.com/minecraft/mc-mods/quark-rotn-edition)
- **Special Mobs.** Selected zombie, skeleton, spider, silverfish, creeper, slime, blaze, ghast, and witch variants. Supported attacks include melee, arrows, contact damage, fireballs, and splash potions. [Special Mobs](https://www.curseforge.com/minecraft/mc-mods/special-mobs)
- **Stygian End Re-Continuation.** Selected ground melee and End Skeleton bow combat. [Stygian End Re-Continuation](https://www.curseforge.com/minecraft/mc-mods/stygian-end-recontinuation)
- **ToroQuest Revamped.** Zombie and Zombie Villager Raider melee after their raid movement. [ToroQuest Revamped](https://www.curseforge.com/minecraft/mc-mods/toroquest-revamped)
- **Unseen's Dungeon Additions.** Ancient Knight and rapier variant close melee outside scripted phases. [Unseen's Dungeon Additions](https://www.curseforge.com/minecraft/mc-mods/unseens-dungeon-additions)
- **Veilings.** Ground melee for the unowned adult Wicked Veiling Custodian and Deft. [Veilings](https://www.curseforge.com/minecraft/mc-mods/veilings)

## Optional integrations for gear, effects, and summons

- **Electroblob's Wizardry.** Summoned zombies earn their own threat from melee and summoned skeletons from arrows once the mob is tracking. [Electroblob's Wizardry](https://www.curseforge.com/minecraft/mc-mods/electroblobs-wizardry)
- **Baubles.** Pack authors can assign threat bonuses or reductions to accessories, which apply while equipped. [Baubles](https://www.curseforge.com/minecraft/mc-mods/baubles)
- **Quality Tools.** Pack authors can configure reforges that raise or lower threat. [Quality Tools](https://www.curseforge.com/minecraft/mc-mods/quality-tools)
- **Just Another Food System.** Pack authors can add a threat modifier to a food's effects. It applies while the effect lasts. [Just Another Food System](https://www.curseforge.com/minecraft/mc-mods/just-another-food-system)
- **Setworks.** Pack authors can configure set bonuses that raise or lower threat while active. [Setworks](https://www.curseforge.com/minecraft/mc-mods/setworks)
- **Statusworks.** A buff or debuff can raise or lower the threat you generate when a pack or addon configures it to do so. A timed defensive buff could help a tank hold aggro with a fixed bonus or a bonus per stack. Use Statusworks 1.3.0 or a later compatible version. In multiplayer, use matching Statusworks versions on the server and clients. [Statusworks](https://www.curseforge.com/minecraft/mc-mods/statusworks) [Statusworks setup](https://github.com/mahghuuuls/threatworks#statusworks-setup)

[Pack setup and integration settings](https://github.com/mahghuuuls/threatworks#optional-integrations)

## For pack authors

In `expanded` and `expanded-all` coverage, use `config/threatworks.cfg` to turn off Threatworks targeting for an entire supported mod in the `modtargeting` section. Add an exact entity ID, such as `minecraft:witch`, to `mobBlacklist` to exclude a single type. Previously saved configurations keep their existing selection. In expanded mode, set `enableVerifiedVanillaMobs=true` to adopt the verified vanilla roster, or use `mobCoverage=expanded-all` to adopt both verified rosters. The blacklist still applies. [Configuration and migration details](https://github.com/mahghuuuls/threatworks#configuration)

Pack authors can also configure gear. Adding `minecraft:iron_chestplate=100` to `itemThreatPointAssignments` doubles threat from direct hits while wearing the chestplate if no other modifiers apply. Restart the server or reopen the single-player world to apply changes.

## For mod developers

Other mods can grant threat in an active encounter, add item bonuses, redirect summon credit, and build player abilities that trigger taunts. Mob-targeting adapters are internal to Threatworks. [Server-side Java API](https://github.com/mahghuuuls/threatworks#integration-api)

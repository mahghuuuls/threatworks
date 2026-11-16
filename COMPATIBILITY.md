# Threatworks 1.0.0 compatibility

Find the verified combat states by entity ID and the supported uses for optional integrations.
Supported targets still depend on each mob's movement, sight, weapon and native combat phase.

## Vanilla defaults

| Entity ID | Supported combat |
| --- | --- |
| `minecraft:zombie` | Ordinary ground melee, including when a qualifying player opens tracking. |
| `minecraft:husk` | Ordinary ground melee. |
| `minecraft:zombie_villager` | Ordinary hostile ground melee. |
| `minecraft:skeleton` | Native bow combat. |
| `minecraft:stray` | Native bow. |
| `minecraft:wither_skeleton` | Native melee. |
| `minecraft:spider` | Ordinary dark, unridden ground melee. |
| `minecraft:cave_spider` | Ordinary dark, unridden ground melee. |
| `minecraft:silverfish` | Ordinary ground melee. |
| `minecraft:endermite` | Ordinary ground melee. |
| `minecraft:vindication_illager` | Unled hostile axe combat with eligible participants. |
| `minecraft:blaze` | Ordinary visible aerial fireball. |
| `minecraft:ghast` | Ordinary visible aerial fireball. |
| `minecraft:slime` | Dry size-four ground hopping and native contact. |
| `minecraft:magma_cube` | Dry size-four ground hopping and native contact. |
| `minecraft:witch` | Native hostile splash potion combat and native drinking handoff. |
| `minecraft:shulker` | Attached End City projectile combat. |
| `minecraft:creeper` | Ground pursuit before fuse commitment. The native fuse can complete or reverse. |
| `minecraft:guardian` | Visible water-lane beam. It can retarget between beams and cancels at the close-range cutoff or on obstruction. |
| `minecraft:elder_guardian` | Visible water-lane beam. An active beam can continue inside three blocks, and native Mining Fatigue still applies. |
| `minecraft:evocation_illager` | Eligible targets with a usable fang lane. Casting, retreat, summoning and the idle sheep spell stay native. |
| `minecraft:vex` | Independent hostile charges between native attack commitments. Evoker summons retain their owner and limited-life behavior. |

## Boss fights

| Entity ID | Supported combat |
| --- | --- |
| `minecraft:wither` | Center-head skull targeting and taunts during normal combat. Spawn invulnerability, side heads and armor follow vanilla behavior. |
| `minecraft:ender_dragon` | Ordinary strafe target selection at the start of a run. Crystal retaliation and End progression follow vanilla behavior. |

Neutral mobs, including Zombie Pigmen and Endermen, follow vanilla targeting.

## Optional integrations

| Mod | Verified version | Supported use |
| --- | --- | --- |
| [Electroblob's Wizardry](https://www.curseforge.com/minecraft/mc-mods/electroblobs-wizardry) | 4.3.19 | Summoned zombies earn threat from melee and summoned skeletons from arrows once the mob is tracking. |
| [Baubles](https://www.curseforge.com/minecraft/mc-mods/baubles) | 1.5.2 | Configured item threat points apply to equipped accessories. |
| [Quality Tools](https://www.curseforge.com/minecraft/mc-mods/quality-tools) | 1.0.7 | Configured qualities and reforges increase or decrease threat generation. |
| [Just Another Food System](https://www.curseforge.com/minecraft/mc-mods/just-another-food-system) | 1.2.0 | Configured food effects contribute threat points while active. |
| [Setworks](https://www.curseforge.com/minecraft/mc-mods/setworks) | 1.1.0 | Configured set bonuses contribute threat points while active. |
| [Statusworks](https://www.curseforge.com/minecraft/mc-mods/statusworks) | 1.3.0 | Configured statuses contribute signed threat points as a fixed value or per active stack. |

The Statusworks integration requires API-compatible Statusworks 1.3.0 or later. In multiplayer, use matching Statusworks versions on the server and clients. The integration works with Java-defined statuses. Use CraftTweaker to define statuses in scripts. [CraftTweaker](https://www.curseforge.com/minecraft/mc-mods/crafttweaker) [Setup and examples](README.md#statusworks-setup)

## Modded mobs

The 217 entity IDs below were verified with the listed mod versions and combat states. Settings that change a mob's AI can affect support.
Fresh configurations enable all 217 listed types when their provider and current combat state pass the live policy checks.
Threatworks uses shared targeting rules where they fit and adds mob-specific checks where needed. The original AI handles movement, attacks, and special moves. Creature mods keep their own dependencies and settings.
Disable Threatworks targeting for an entire namespace under `modtargeting` in `config/threatworks.cfg`. Use `mobBlacklist` to exclude an exact ID. See the [configuration and migration instructions](README.md#configuration).

### `ancientwarfarenpc`

Verified provider file `ancientwarfare-1.12.2-2.9.20.jar`.

| Entity ID | Verified combat and state |
| --- | --- |
| `ancientwarfarenpc:faction.archer` | Visible native bow strikes within native weapon and home bounds. The faction must treat the player as hostile. |
| `ancientwarfarenpc:faction.archer.elite` | Visible native bow strikes within native weapon and home bounds. The faction must treat the player as hostile. |
| `ancientwarfarenpc:faction.leader` | Dry native melee within native weapon reach. The faction must treat the player as hostile. |
| `ancientwarfarenpc:faction.leader.elite` | Dry native melee within native weapon reach. The faction must treat the player as hostile. |
| `ancientwarfarenpc:faction.soldier` | Dry native melee within native weapon reach. The faction must treat the player as hostile. |
| `ancientwarfarenpc:faction.soldier.elite` | Dry native melee within native weapon reach. The faction must treat the player as hostile. |

### `betterendforge`

Verified provider file `BetterEndForge-Unofficial-GBPort-1.12.2-1.3.9.jar`.

| Entity ID | Verified combat and state |
| --- | --- |
| `betterendforge:end_slime` | Dry size-four ground hopping and native contact. |

### `charm`

Verified provider file `Charm-1.12.2-1.4.1.jar`.

| Entity ID | Verified combat and state |
| --- | --- |
| `charm:spectre` | Dark ground pursuit and native melee before despawn. |

### `cqrepoured`

Verified provider file `Chocolate_Quest_Repoured-1.12.2-2.8.0B.jar`.

| Entity ID | Verified combat and state |
| --- | --- |
| `cqrepoured:boarman` | Visible close unarmed melee against players of hostile standing while dry, unmounted and leaderless. |
| `cqrepoured:gremlin` | Visible close unarmed melee against players of hostile standing while dry, unmounted and leaderless. |
| `cqrepoured:illager` | Visible close unarmed melee against players of hostile standing while dry, unmounted and leaderless. |
| `cqrepoured:mandril` | Visible close unarmed melee against players of hostile standing while dry, unmounted and leaderless. |
| `cqrepoured:minotaur` | Visible close unarmed melee against players of hostile standing while dry, unmounted and leaderless. |
| `cqrepoured:mummy` | Visible close unarmed melee against players of hostile standing while dry, unmounted and leaderless. |
| `cqrepoured:ogre` | Visible close unarmed melee against players of hostile standing while dry, unmounted and leaderless. |
| `cqrepoured:orc` | Visible close unarmed melee against players of hostile standing while dry, unmounted and leaderless. |
| `cqrepoured:pirate` | Visible close unarmed melee against players of hostile standing while dry, unmounted and leaderless. |
| `cqrepoured:skeleton` | Visible close unarmed melee against players of hostile standing while dry, unmounted and leaderless. |
| `cqrepoured:spectre` | Visible close unarmed melee against players of hostile standing while dry, unmounted and leaderless. |
| `cqrepoured:walker` | Visible close unarmed melee against players of hostile standing while dry, unmounted and leaderless. |
| `cqrepoured:zombie` | Visible close melee against hostile players while dry, unmounted and leaderless. Empty hands or an iron sword with empty offhand. |

### `da`

Verified provider file `unseens_dungeon_additions-1.7.5.jar`.

| Entity ID | Verified combat and state |
| --- | --- |
| `da:ancient_knight` | Dry, close timed native attack on level ground outside scripted combat phases. |
| `da:ancient_knight_rapier` | Dry, close timed native attack on level ground outside scripted combat phases. |

### `deeperdepths`

Verified provider file `Deeper-Depths-1.12.2-1.1.2a.jar`.

| Entity ID | Verified combat and state |
| --- | --- |
| `deeperdepths:bogged` | Native bow and poison arrows. |

### `defiledlands`

Verified provider file `defiledlands-1.12.2-1.4.3.jar`.

| Entity ID | Verified combat and state |
| --- | --- |
| `defiledlands:host` | Ordinary dry ground melee and pursuit. |
| `defiledlands:scuttler` | Ordinary dry ground melee and pursuit. |
| `defiledlands:shambler` | Ordinary dry ground melee and pursuit. |
| `defiledlands:shambler_twisted` | Ordinary dry ground melee and pursuit. |
| `defiledlands:slime_defiled` | Dry size-four ground hopping and native contact. |

### `dungeonmobs`

Verified provider file `dungeonmobs-5.9.0-MC1.12.2.jar`.

| Entity ID | Verified combat and state |
| --- | --- |
| `dungeonmobs:dmghost` | Dry, dark native melee. Use an enchanted weapon for ordinary melee against the Ghost. |
| `dungeonmobs:dmghoul` | Unridden native ground melee. |
| `dungeonmobs:dmhookhorror` | Ordinary ground melee with native armor damage. |
| `dungeonmobs:dmrevenant` | Dry, dark native melee outside the burning phase. |
| `dungeonmobs:dmumberhulk` | Ordinary ground melee with native nausea. |

### `eerieentities`

Verified provider file `EerieEntities-1.12.2-1.0.7.jar`.

| Entity ID | Verified combat and state |
| --- | --- |
| `eerieentities:cursed_armor` | Ordinary dry, unridden ground melee. |
| `eerieentities:nether_knight` | Ordinary dry, unridden ground melee. |

### `grimoireofgaia`

Verified provider file `GrimoireOfGaia3-1.12.2-1.7.2.jar`.

| Entity ID | Verified combat and state |
| --- | --- |
| `grimoireofgaia:ant` | Ordinary dry ground melee. |
| `grimoireofgaia:ant_ranger` | Visible native magic while exposed. Hiding yields to native targeting. |
| `grimoireofgaia:anubis` | Visible native magic projectile. |
| `grimoireofgaia:arachne` | Visible native magic. Climbing yields to native targeting. |
| `grimoireofgaia:baphomet` | Visible native fireball. |
| `grimoireofgaia:beholder` | Visible native magic projectile. |
| `grimoireofgaia:bone_knight` | Dry ground melee at night on Normal difficulty. |
| `grimoireofgaia:butler` | Ordinary dry ground melee. |
| `grimoireofgaia:cecaelia` | Ordinary dry ground melee. |
| `grimoireofgaia:cobblestone_golem` | Ordinary dry ground melee. |
| `grimoireofgaia:deathword` | Ordinary dry ground melee. |
| `grimoireofgaia:dhampir` | Ordinary dry ground melee. |
| `grimoireofgaia:dullahan` | Unmounted dry ground melee. |
| `grimoireofgaia:flesh_lich` | Fireball combat with a clear projectile lane in the qualified visible range. |
| `grimoireofgaia:gelatinous_slime` | Ordinary dry ground melee. |
| `grimoireofgaia:goblin_feral` | Native bow form. |
| `grimoireofgaia:illager_fire` | Visible native fireball. |
| `grimoireofgaia:illager_inquisitor` | Ordinary dry ground melee. |
| `grimoireofgaia:kobold` | Native bow and dry ground melee. |
| `grimoireofgaia:mandragora` | Ordinary dry ground melee. |
| `grimoireofgaia:matango` | Ordinary dry ground melee. |
| `grimoireofgaia:mimic` | Ordinary dry ground melee. |
| `grimoireofgaia:minotaur` | Ordinary dry ground melee. |
| `grimoireofgaia:minotaurus` | Ordinary dry ground melee. |
| `grimoireofgaia:mite` | Dry ground melee at night on Normal difficulty. |
| `grimoireofgaia:mummy` | Ordinary dry ground melee. |
| `grimoireofgaia:naga` | Ordinary dry ground melee. |
| `grimoireofgaia:nine_tails` | Visible native fireball. |
| `grimoireofgaia:oni` | Ordinary dry ground melee. |
| `grimoireofgaia:orc` | Ordinary dry ground melee. |
| `grimoireofgaia:selkie` | Native bow and dry ground melee. |
| `grimoireofgaia:shaman` | Native poison splash against eligible living targets. |
| `grimoireofgaia:sharko` | Ordinary dry ground melee. |
| `grimoireofgaia:siren` | Native bow and dry ground melee. |
| `grimoireofgaia:sludge_girl` | Ordinary dry ground melee. |
| `grimoireofgaia:sphinx` | Ordinary dry ground melee. |
| `grimoireofgaia:sporeling` | Ordinary dry ground melee. |
| `grimoireofgaia:spriggan` | Ordinary dry ground melee. |
| `grimoireofgaia:succubus` | Ordinary dry ground melee. |
| `grimoireofgaia:vampire` | Dry ground melee at night on Normal difficulty. |
| `grimoireofgaia:witch` | Native poison splash. |
| `grimoireofgaia:wither_cow` | Ordinary dry ground melee. |
| `grimoireofgaia:yeti` | Ordinary dry ground melee. |

### `iceandfire`

Verified provider file `IceAndFireRotN-1.9.1-1.3.4.jar`.

| Entity ID | Verified combat and state |
| --- | --- |
| `iceandfire:dread_beast` | Post-emergence ground pursuit and native bite. |
| `iceandfire:dread_ghoul` | Post-emergence ground pursuit and native slashes. |
| `iceandfire:dread_knight` | Unmounted, uncommanded post-emergence ground melee. |
| `iceandfire:dread_scuttler` | Post-emergence visible native bite outside wall ascent. |
| `iceandfire:dread_thrall` | Uncommanded post-emergence ground melee. |

### `illagers_plus`

Verified provider file `IllagersPlus-1.12.2-1.1.3.jar`.

| Entity ID | Verified combat and state |
| --- | --- |
| `illagers_plus:archer` | Native bow. |
| `illagers_plus:furantur` | Ordinary dry melee at night. |

### `magma_monsters`

Verified provider file `MagmaMonsters-0.3.0.jar`.

| Entity ID | Verified combat and state |
| --- | --- |
| `magma_monsters:magma_monster` | Cooled dry ground melee. The molten phase uses native targeting. |
| `magma_monsters:magma_monster_grunt` | Cooled dry ground melee. The molten phase uses native targeting. |

### `mocreatures`

Verified provider file `mocreatures-12.5.1.jar`.

| Entity ID | Verified combat and state |
| --- | --- |
| `mocreatures:cavescorpion` | Ordinary dark, dry ground melee. |
| `mocreatures:dirtscorpion` | Ordinary dark, dry ground melee. |
| `mocreatures:firescorpion` | Ordinary dark, dry ground melee. |
| `mocreatures:frostscorpion` | Ordinary dark, dry ground melee. |
| `mocreatures:horsemob` | Wild hostile ground form 23. |
| `mocreatures:silverskeleton` | Ordinary dark, dry ground melee. |
| `mocreatures:undeadscorpion` | Ordinary dark, dry ground melee. |

### `mod_lavacow`

Verified provider file `Fish's Undead Rising-1.6.2.jar`.

| Entity ID | Verified combat and state |
| --- | --- |
| `mod_lavacow:amber_scarab` | Wild, unridden dry ground melee at night. |
| `mod_lavacow:avaton` | Visible local flight charge or melee. Committed charges and casts use native targeting. |
| `mod_lavacow:banshee` | Visible local flight charge or melee. Committed charges and casts use native targeting. |
| `mod_lavacow:cactoid` | Wild, unridden dry ground melee at night. |
| `mod_lavacow:foglet` | Wild, unridden dry ground melee at night. |
| `mod_lavacow:ghoul` | Wild timed dry melee against wounded players the Ghoul can pursue. |
| `mod_lavacow:grave_robber_ghost` | Visible local flight charge or melee. Committed charges and casts use native targeting. |
| `mod_lavacow:imp` | Wild, unridden dry ground melee at night. |
| `mod_lavacow:isnachi` | Wild, unridden dry ground melee at night. |
| `mod_lavacow:lilsludge` | Wild, unridden dry ground melee at night. |
| `mod_lavacow:mummy` | Wild, unowned, dry ground melee at night. |
| `mod_lavacow:pingu` | Wild, unridden dry ground melee at night. |
| `mod_lavacow:sea_hag` | Visible local flight charge or melee. Committed charges and casts use native targeting. |
| `mod_lavacow:unburied` | Wild dry ground melee at night after rising. |
| `mod_lavacow:weta` | Wild dry ground melee at night outside the feeding phase. |
| `mod_lavacow:wraith` | Visible local flight charge or melee. Committed charges and casts use native targeting. |
| `mod_lavacow:zombiefrozen` | Wild, unridden dry ground melee at night. |
| `mod_lavacow:zombiemushroom` | Wild, unridden dry ground melee at night. |

### `mowziesmobs`

Verified provider file `mowziesmobs_1.12.2_1.5.8.jar`.

| Entity ID | Verified combat and state |
| --- | --- |
| `mowziesmobs:foliaath` | Stationary bite while active and in reach. Rising and retracting use native targeting. |

### `natura`

Verified provider file `natura-legacy-1.12.2-4.6.1.jar`.

| Entity ID | Verified combat and state |
| --- | --- |
| `natura:babyheatscarspider` | Dark ground melee outside climbing routes. |
| `natura:heatscarspider` | Dark ground melee outside climbing routes. |
| `natura:nitrocreeper` | Dry ground pursuit with native fuse and blast. |

### `pvj`

Verified provider file `ProjectVibrantJourneys-1.12.2-1.6.4.jar`.

| Entity ID | Verified combat and state |
| --- | --- |
| `pvj:pvj_banshee` | Nighttime ground melee outside the burning phase. |
| `pvj:pvj_shade` | Nighttime ground melee outside the burning phase. |

### `quark`

Verified provider file `QuarkRotN-r1.6-192.jar`.

| Entity ID | Verified combat and state |
| --- | --- |
| `quark:ashen` | Native bow outside wolf avoidance. |
| `quark:dweller` | Ordinary dry native ground melee. |
| `quark:forgotten` | Native bow and native sword after its weapon switch. |
| `quark:pirate` | Native iron-sword melee. |
| `quark:wraith` | Ordinary dry native ground melee. |

### `specialmobs`

Verified provider file `specialmobs-1.0.4_for_mc1.12.2.jar`.

| Entity ID | Verified combat and state |
| --- | --- |
| `specialmobs:babycavespider` | Dark, dry, unridden ground melee. |
| `specialmobs:babyghast` | Visible local flight and native melee. |
| `specialmobs:babyspider` | Dark, dry, unridden ground melee. |
| `specialmobs:blindingsilverfish` | Ordinary dry ground melee. |
| `specialmobs:blueberryslime` | Dry size-four ground hopping and native contact. |
| `specialmobs:bruteskeleton` | Native bow combat. |
| `specialmobs:brutewitherskeleton` | Native ground melee with the supported weapon. |
| `specialmobs:brutezombie` | Ordinary dry ground melee. |
| `specialmobs:caramelslime` | Dry size-four ground hopping and native contact. |
| `specialmobs:cinderblaze` | Airborne native melee against eligible targets. |
| `specialmobs:conflagrationblaze` | Visible native airborne fireballs. |
| `specialmobs:darkcreeper` | Ordinary dry ground pursuit of an eligible target and the native fuse and blast. |
| `specialmobs:deathcreeper` | Ordinary dry ground pursuit of an eligible target and the native fuse and blast. |
| `specialmobs:desertspider` | Dark, dry, unridden ground melee. |
| `specialmobs:dirtcreeper` | Ordinary dry ground pursuit of an eligible target and the native fuse and blast. |
| `specialmobs:dominationwitch` | Visible, near-level native splash potions. Native drinking and mounted phases retain native targeting. |
| `specialmobs:doomcreeper` | Ordinary dry ground pursuit of an eligible target and the native fuse and blast. |
| `specialmobs:drowningcreeper` | Ordinary dry ground pursuit of an eligible target and the native fuse and blast. |
| `specialmobs:emberblaze` | Airborne native melee against eligible targets. |
| `specialmobs:fighterghast` | Visible local flight and native melee. |
| `specialmobs:firecreeper` | Ordinary dry ground pursuit of an eligible target and the native fuse and blast. |
| `specialmobs:fireskeleton` | Native bow combat. |
| `specialmobs:firezombie` | Ordinary dry ground melee. |
| `specialmobs:gatlingskeleton` | Native bow combat. |
| `specialmobs:gatlingwitherskeleton` | Native bow combat. |
| `specialmobs:giantskeleton` | Native bow combat. |
| `specialmobs:giantspider` | Dark, dry, unridden ground melee. |
| `specialmobs:giantwitherskeleton` | Native ground melee with the supported weapon. |
| `specialmobs:giantzombie` | Ordinary dry ground melee. |
| `specialmobs:grapeslime` | Dry size-four ground hopping and native contact. |
| `specialmobs:gravelcreeper` | Ordinary dry ground pursuit of an eligible target and the native fuse and blast. |
| `specialmobs:hardenedlavaslime` | Dry size-four ground hopping and native contact. |
| `specialmobs:hellfireblaze` | Visible native airborne fireballs. |
| `specialmobs:hungryspider` | Dark, dry, unridden ground melee. |
| `specialmobs:hungryzombie` | Ordinary dry ground melee. |
| `specialmobs:huskzombie` | Ordinary dry ground melee with the supported goal set. Sprint-goal and mounted variants use native targeting. |
| `specialmobs:infernoblaze` | Visible native airborne fireballs. |
| `specialmobs:joltblaze` | Airborne native melee against eligible targets. |
| `specialmobs:jumpingcreeper` | Dry ground pursuit, native leap, and native fuse and blast. |
| `specialmobs:kingghast` | Visible native airborne fireballs. |
| `specialmobs:knightskeleton` | Native ground melee with the supported weapon. |
| `specialmobs:knightwitherskeleton` | Native ground melee with the supported weapon. |
| `specialmobs:lemonslime` | Dry size-four ground hopping and native contact. |
| `specialmobs:lightningcreeper` | Ordinary dry ground pursuit of an eligible target and the native fuse and blast. |
| `specialmobs:minicreeper` | Ordinary dry ground pursuit of an eligible target and the native fuse and blast. |
| `specialmobs:mothercavespider` | Dark, dry, unridden ground melee. |
| `specialmobs:motherspider` | Dark, dry, unridden ground melee. |
| `specialmobs:palespider` | Dark, dry, unridden ground melee. |
| `specialmobs:plaguezombie` | Ordinary dry ground melee. |
| `specialmobs:poisonsilverfish` | Ordinary dry ground melee. |
| `specialmobs:poisonskeleton` | Native bow combat. |
| `specialmobs:poisonspider` | Dark, dry, unridden ground melee. |
| `specialmobs:queenghast` | Visible native airborne fireballs. |
| `specialmobs:shadowswitch` | Visible, near-level native splash potions. Native drinking and mounted phases retain native targeting. |
| `specialmobs:sniperskeleton` | Native bow combat. |
| `specialmobs:sniperwitherskeleton` | Native bow combat. |
| `specialmobs:specialblaze` | Visible native airborne fireballs. |
| `specialmobs:specialcavespider` | Dark, dry, unridden ground melee. |
| `specialmobs:specialcreeper` | Ordinary dry ground pursuit of an eligible target and the native fuse and blast. |
| `specialmobs:specialghast` | Visible native airborne fireballs. |
| `specialmobs:speciallavaslime` | Dry size-four ground hopping and native contact. |
| `specialmobs:specialsilverfish` | Ordinary dry ground melee. |
| `specialmobs:specialskeleton` | Native bow combat. |
| `specialmobs:specialslime` | Dry size-four ground hopping and native contact. |
| `specialmobs:specialspider` | Dark, dry, unridden ground melee. |
| `specialmobs:specialwitch` | Visible, near-level native splash potions. Native drinking and mounted phases retain native targeting. |
| `specialmobs:specialwitherskeleton` | Native ground melee with the supported weapon. |
| `specialmobs:specialzombie` | Ordinary dry ground melee. |
| `specialmobs:splittingcreeper` | Ordinary dry ground pursuit of an eligible target and the native fuse and blast. |
| `specialmobs:stickylavaslime` | Dry size-four ground hopping and native contact. |
| `specialmobs:strawberryslime` | Dry size-four ground hopping and native contact. |
| `specialmobs:strayskeleton` | Native bow combat. |
| `specialmobs:toughsilverfish` | Ordinary dry ground melee. |
| `specialmobs:undeadwitch` | Visible, near-level native splash potions. Native drinking and mounted phases retain native targeting. |
| `specialmobs:unholyghast` | Visible local flight and native melee. |
| `specialmobs:watermelonslime` | Dry size-four ground hopping and native contact. |
| `specialmobs:webcavespider` | Dark, dry, unridden ground melee. |
| `specialmobs:webspider` | Dark, dry, unridden ground melee. |
| `specialmobs:wildfireblaze` | Visible native airborne fireballs. |
| `specialmobs:witchcavespider` | Dark, dry, unridden ground melee. |
| `specialmobs:witchspider` | Dark, dry, unridden ground melee. |

### `stygian`

Verified provider file `stygian-2.1.2.jar`.

| Entity ID | Verified combat and state |
| --- | --- |
| `stygian:cracky` | Ordinary dry, unridden ground melee. |
| `stygian:end_skeleton` | Visible native bow combat. |
| `stygian:gluttermite` | Ordinary dry, unridden ground melee. |
| `stygian:lunar_zorp` | Adult dry ground melee outside the native leap. |
| `stygian:skipper` | Dry grounded melee outside the native low-health, fuse or climb phase. |
| `stygian:stygian_endermite` | Ordinary dry, unridden ground melee. |
| `stygian:supaika` | Dry ground melee in reach outside the native low-health or committed slam phase. |
| `stygian:watchling` | Adult dry, unridden ground melee. |

### `toroquest`

Verified provider file `ToroQuest-Revamped-3-25-2022-fix.jar`.

| Entity ID | Verified combat and state |
| --- | --- |
| `toroquest:toroquest_zombie_raider` | Ordinary player melee after native raid movement. |
| `toroquest:toroquest_zombie_villager_raider` | Ordinary player melee after native raid movement. |

### `veilings`

Verified provider file `veilings-1.2.1.jar`.

| Entity ID | Verified combat and state |
| --- | --- |
| `veilings:wicked_veiling_custodian` | Unowned adult dry ground melee. |
| `veilings:wicked_veiling_deft` | Unowned adult dry ground melee. |

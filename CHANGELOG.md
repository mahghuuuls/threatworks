# Changelog

## 1.1.0

- Expanded support for built-in [Electroblob's Wizardry](https://www.curseforge.com/minecraft/mc-mods/electroblobs-wizardry) combat summons, including Magic Slime host damage and spell-created Snow Golem hits against Blazes. Attributable damage that removes health earns threat for the summon in an active encounter.
- Added a read-only server API for addon authors to query one participant's threat, eligibility, confirmed aggro, and taunt status. The API can also report the takeover score for a settled ordinary zombie or skeleton encounter when it has enough information.

## 1.0.0

- Added threat targeting for supported vanilla and modded hostile mobs, with default coverage for verified types, per-mod switches, and an exact entity blacklist.
- Added health-loss-based threat, distance-sensitive target switching, opening and sight grace rules, and taunts through the server API.
- Added Wither center-head targeting and Ender Dragon ordinary strafe targeting while preserving their native special attacks and progression.
- Added configurable gear threat points and optional support for Wizardry summons, Baubles, Quality Tools, Just Another Food System and Setworks.
- Added optional Statusworks integration for signed fixed and per-stack threat modifiers on active statuses.
- Added operator inspection for current targets and participants' threat scores.

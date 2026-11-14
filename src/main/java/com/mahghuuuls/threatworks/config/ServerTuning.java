package com.mahghuuuls.threatworks.config;

import com.mahghuuuls.threatworks.threat.Encounter.Rules;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Effective values for one server run. Loading never rewrites rejected input. */
public final class ServerTuning {
    public static final double BASE_THREAT_PER_HEALTH_LOST = 1.0;
    public static final double POINT_SCALE = 100.0;
    public static final int TICKS_PER_SECOND = 20;
    // Bound each input independently; the collector also handles totals beyond the double range.
    public static final double MAX_ITEM_POINTS = Double.MAX_VALUE / 8.0;

    private static final String CATEGORY = "server";
    private static final double ORDINARY_LEAD = 0.2;
    private static final double MAXIMUM_LEAD = 0.4;
    private static final double CHASE_START = 8.0;
    private static final double CHASE_END = 16.0;
    private static final double OPENING_THREAT = 10.0;
    private static final double TAUNT_SECONDS = 3.0;
    private static final double RESET_SECONDS = 10.0;
    private static final double SIGHT_SECONDS = 2.0;
    private static final double MAX_SECONDS = (double) Integer.MAX_VALUE / TICKS_PER_SECOND;
    private static final Pattern NAMESPACED_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public enum MobType {
        ZOMBIE("minecraft:zombie", true),
        HUSK("minecraft:husk", true),
        ZOMBIE_VILLAGER("minecraft:zombie_villager", true),
        SKELETON("minecraft:skeleton", true),
        STRAY("minecraft:stray", true),
        WITHER_SKELETON("minecraft:wither_skeleton", true),
        SPIDER("minecraft:spider", true),
        CAVE_SPIDER("minecraft:cave_spider", true),
        SILVERFISH("minecraft:silverfish", true),
        ENDERMITE("minecraft:endermite", true),
        VINDICATOR("minecraft:vindication_illager", true),
        BLAZE("minecraft:blaze", true),
        GHAST("minecraft:ghast", true),
        SLIME("minecraft:slime", true),
        MAGMA_CUBE("minecraft:magma_cube", true),
        WITCH("minecraft:witch", true),
        SHULKER("minecraft:shulker", true),
        SPECIAL_ZOMBIE("specialmobs:specialzombie", true),
        PVJ_BANSHEE("pvj:pvj_banshee"),
        PVJ_SHADE("pvj:pvj_shade"),
        CHARM_SPECTRE("charm:spectre"),
        MOC_HORSE_MOB("mocreatures:horsemob"),
        SPECIAL_GHAST("specialmobs:specialghast"),
        KING_GHAST("specialmobs:kingghast"),
        QUEEN_GHAST("specialmobs:queenghast"),
        BABY_GHAST("specialmobs:babyghast"),
        FIGHTER_GHAST("specialmobs:fighterghast"),
        UNHOLY_GHAST("specialmobs:unholyghast"),
        SPECIAL_WITCH("specialmobs:specialwitch"),
        DOMINATION_WITCH("specialmobs:dominationwitch"),
        SHADOWS_WITCH("specialmobs:shadowswitch"),
        UNDEAD_WITCH("specialmobs:undeadwitch"),
        SPECIAL_SLIME("specialmobs:specialslime"),
        BLUEBERRY_SLIME("specialmobs:blueberryslime"),
        CARAMEL_SLIME("specialmobs:caramelslime"),
        GRAPE_SLIME("specialmobs:grapeslime"),
        LEMON_SLIME("specialmobs:lemonslime"),
        STRAWBERRY_SLIME("specialmobs:strawberryslime"),
        WATERMELON_SLIME("specialmobs:watermelonslime"),
        SPECIAL_LAVA_SLIME("specialmobs:speciallavaslime"),
        HARDENED_LAVA_SLIME("specialmobs:hardenedlavaslime"),
        STICKY_LAVA_SLIME("specialmobs:stickylavaslime"),
        BRUTE_ZOMBIE("specialmobs:brutezombie"),
        FIRE_ZOMBIE("specialmobs:firezombie"),
        GIANT_ZOMBIE("specialmobs:giantzombie"),
        HUNGRY_ZOMBIE("specialmobs:hungryzombie"),
        HUSK_ZOMBIE("specialmobs:huskzombie"),
        PLAGUE_ZOMBIE("specialmobs:plaguezombie"),
        SPECIAL_SKELETON("specialmobs:specialskeleton"),
        BRUTE_SKELETON("specialmobs:bruteskeleton"),
        FIRE_SKELETON("specialmobs:fireskeleton"),
        GATLING_SKELETON("specialmobs:gatlingskeleton"),
        GIANT_SKELETON("specialmobs:giantskeleton"),
        KNIGHT_SKELETON("specialmobs:knightskeleton"),
        POISON_SKELETON("specialmobs:poisonskeleton"),
        SNIPER_SKELETON("specialmobs:sniperskeleton"),
        STRAY_SPECIAL_SKELETON("specialmobs:strayskeleton"),
        SPECIAL_WITHER_SKELETON("specialmobs:specialwitherskeleton"),
        BRUTE_WITHER_SKELETON("specialmobs:brutewitherskeleton"),
        GATLING_WITHER_SKELETON("specialmobs:gatlingwitherskeleton"),
        GIANT_WITHER_SKELETON("specialmobs:giantwitherskeleton"),
        KNIGHT_WITHER_SKELETON("specialmobs:knightwitherskeleton"),
        SNIPER_WITHER_SKELETON("specialmobs:sniperwitherskeleton"),
        SPECIAL_SILVERFISH("specialmobs:specialsilverfish"),
        BLINDING_SILVERFISH("specialmobs:blindingsilverfish"),
        POISON_SILVERFISH("specialmobs:poisonsilverfish"),
        TOUGH_SILVERFISH("specialmobs:toughsilverfish"),
        SPECIAL_SPIDER("specialmobs:specialspider"),
        SPECIAL_CAVE_SPIDER("specialmobs:specialcavespider"),
        BABY_SPIDER("specialmobs:babyspider"),
        BABY_CAVE_SPIDER("specialmobs:babycavespider"),
        DESERT_SPIDER("specialmobs:desertspider"),
        GIANT_SPIDER("specialmobs:giantspider"),
        PALE_SPIDER("specialmobs:palespider"),
        POISON_SPIDER("specialmobs:poisonspider"),
        WITCH_SPIDER("specialmobs:witchspider"),
        WITCH_CAVE_SPIDER("specialmobs:witchcavespider"),
        HUNGRY_SPIDER("specialmobs:hungryspider"),
        MOTHER_SPIDER("specialmobs:motherspider"),
        MOTHER_CAVE_SPIDER("specialmobs:mothercavespider"),
        WEB_SPIDER("specialmobs:webspider"),
        WEB_CAVE_SPIDER("specialmobs:webcavespider"),
        SPECIAL_CREEPER("specialmobs:specialcreeper"),
        FIRE_CREEPER("specialmobs:firecreeper"),
        DEATH_CREEPER("specialmobs:deathcreeper"),
        DOOM_CREEPER("specialmobs:doomcreeper"),
        MINI_CREEPER("specialmobs:minicreeper"),
        DARK_CREEPER("specialmobs:darkcreeper"),
        GRAVEL_CREEPER("specialmobs:gravelcreeper"),
        LIGHTNING_CREEPER("specialmobs:lightningcreeper"),
        SPLITTING_CREEPER("specialmobs:splittingcreeper"),
        DIRT_CREEPER("specialmobs:dirtcreeper"),
        DROWNING_CREEPER("specialmobs:drowningcreeper"),
        JUMPING_CREEPER("specialmobs:jumpingcreeper"),
        SPECIAL_BLAZE("specialmobs:specialblaze"),
        CINDER_BLAZE("specialmobs:cinderblaze"),
        CONFLAGRATION_BLAZE("specialmobs:conflagrationblaze"),
        EMBER_BLAZE("specialmobs:emberblaze"),
        HELLFIRE_BLAZE("specialmobs:hellfireblaze"),
        INFERNO_BLAZE("specialmobs:infernoblaze"),
        JOLT_BLAZE("specialmobs:joltblaze"),
        WILDFIRE_BLAZE("specialmobs:wildfireblaze"),
        DEFILED_HOST("defiledlands:host"),
        DEFILED_SHAMBLER("defiledlands:shambler"),
        DEFILED_TWISTED_SHAMBLER("defiledlands:shambler_twisted"),
        DEFILED_SCUTTLER("defiledlands:scuttler"),
        DUNGEON_GHOUL("dungeonmobs:dmghoul"),
        DUNGEON_HOOK_HORROR("dungeonmobs:dmhookhorror"),
        DUNGEON_UMBER_HULK("dungeonmobs:dmumberhulk"),
        DREAD_THRALL("iceandfire:dread_thrall"),
        DREAD_KNIGHT("iceandfire:dread_knight"),
        DREAD_BEAST("iceandfire:dread_beast"),
        DREAD_GHOUL("iceandfire:dread_ghoul"),
        DREAD_SCUTTLER("iceandfire:dread_scuttler"),
        GAIA_MUMMY("grimoireofgaia:mummy"),
        GAIA_SLUDGE_GIRL("grimoireofgaia:sludge_girl"),
        GAIA_SUCCUBUS("grimoireofgaia:succubus"),
        GAIA_WITHER_COW("grimoireofgaia:wither_cow"),
        GAIA_ANT("grimoireofgaia:ant"),
        GAIA_COBBLESTONE_GOLEM("grimoireofgaia:cobblestone_golem"),
        GAIA_DULLAHAN("grimoireofgaia:dullahan"),
        GAIA_GELATINOUS_SLIME("grimoireofgaia:gelatinous_slime"),
        GAIA_ILLAGER_BUTLER("grimoireofgaia:butler"),
        GAIA_ILLAGER_INQUISITOR("grimoireofgaia:illager_inquisitor"),
        GAIA_SPORELING("grimoireofgaia:sporeling"),
        GAIA_YETI("grimoireofgaia:yeti"),
        GAIA_MANDRAGORA("grimoireofgaia:mandragora"),
        GAIA_MATANGO("grimoireofgaia:matango"),
        GAIA_MIMIC("grimoireofgaia:mimic"),
        GAIA_SPHINX("grimoireofgaia:sphinx"),
        GAIA_SPRIGGAN("grimoireofgaia:spriggan"),
        GAIA_DEATHWORD("grimoireofgaia:deathword"),
        GAIA_DHAMPIR("grimoireofgaia:dhampir"),
        GAIA_MINOTAUR("grimoireofgaia:minotaur"),
        GAIA_NAGA("grimoireofgaia:naga"),
        GAIA_ONI("grimoireofgaia:oni"),
        GAIA_SHARKO("grimoireofgaia:sharko"),
        FISH_FOGLET("mod_lavacow:foglet"),
        FISH_IMP("mod_lavacow:imp"),
        FISH_ISNACHI("mod_lavacow:isnachi"),
        FISH_PINGU("mod_lavacow:pingu"),
        FISH_MUMMY("mod_lavacow:mummy"),
        FISH_UNBURIED("mod_lavacow:unburied"),
        FISH_FROZEN_ZOMBIE("mod_lavacow:zombiefrozen"),
        FISH_MUSHROOM_ZOMBIE("mod_lavacow:zombiemushroom"),
        FISH_SCARAB("mod_lavacow:amber_scarab"),
        FISH_LITTLE_SLUDGE("mod_lavacow:lilsludge"),
        FISH_WETA("mod_lavacow:weta"),
        FISH_CACTOID("mod_lavacow:cactoid"),
        STYGIAN_CRACKY("stygian:cracky"),
        STYGIAN_GLUTTERMITE("stygian:gluttermite"),
        STYGIAN_ENDERMITE("stygian:stygian_endermite"),
        STYGIAN_WATCHLING("stygian:watchling"),
        EERIE_CURSED_ARMOR("eerieentities:cursed_armor"),
        EERIE_NETHER_KNIGHT("eerieentities:nether_knight"),
        UNSEEN_ANCIENT_KNIGHT("da:ancient_knight"),
        UNSEEN_KNIGHT_RAPIER("da:ancient_knight_rapier"),
        CQR_BOARMAN("cqrepoured:boarman"),
        CQR_GREMLIN("cqrepoured:gremlin"),
        CQR_ILLAGER("cqrepoured:illager"),
        CQR_MANDRIL("cqrepoured:mandril"),
        CQR_MINOTAUR("cqrepoured:minotaur"),
        CQR_MUMMY("cqrepoured:mummy"),
        CQR_OGRE("cqrepoured:ogre"),
        CQR_ORC("cqrepoured:orc"),
        CQR_PIRATE("cqrepoured:pirate"),
        CQR_SKELETON("cqrepoured:skeleton"),
        CQR_SPECTRE("cqrepoured:spectre"),
        CQR_WALKER("cqrepoured:walker"),
        CQR_ZOMBIE("cqrepoured:zombie"),
        QUARK_ASHEN("quark:ashen"),
        QUARK_DWELLER("quark:dweller"),
        QUARK_FORGOTTEN("quark:forgotten"),
        QUARK_PIRATE("quark:pirate"),
        QUARK_WRAITH("quark:wraith"),
        GAIA_BONE_KNIGHT("grimoireofgaia:bone_knight"),
        GAIA_MITE("grimoireofgaia:mite"),
        GAIA_VAMPIRE("grimoireofgaia:vampire"),
        ILLAGERS_FURANTUR("illagers_plus:furantur"),
        FOLIAATH("mowziesmobs:foliaath");

        public final String id;
        private final boolean defaultEnabled;

        MobType(String id) {
            this(id, false);
        }

        MobType(String id, boolean defaultEnabled) {
            this.id = id;
            this.defaultEnabled = defaultEnabled;
        }

        private static MobType find(String id) {
            for (MobType type : values()) {
                if (type.id.equals(id)) {
                    return type;
                }
            }
            return null;
        }
    }

    private final Set<MobType> enabled;
    private final Set<String> blacklist;
    private final Map<MobType, Rules> rules;
    private final Map<String, Map<Integer, Double>> assignments;

    private ServerTuning(Set<MobType> enabled, Set<String> blacklist, Map<MobType, Rules> rules,
                         Map<String, Map<Integer, Double>> assignments) {
        // These collections are built exclusively by the loader and never escape.
        this.enabled = enabled;
        this.blacklist = blacklist;
        this.rules = rules;
        this.assignments = assignments;
    }

    public boolean isEnabled(MobType type) {
        return enabled.contains(type);
    }

    public boolean isBlacklisted(String id) {
        return blacklist.contains(id);
    }

    public Rules rulesFor(MobType type) {
        return rules.get(type);
    }

    /** A null metadata key means item-wide; a null result means there is no such assignment. */
    public Double assignedPoints(String itemId, Integer metadata) {
        Map<Integer, Double> variants = assignments.get(itemId);
        return variants == null ? null : variants.get(metadata);
    }

    public static ServerTuning load(File file, Predicate<String> registeredItem, Consumer<String> warn) {
        Configuration fileConfig = new Configuration(file);
        fileConfig.setCategoryComment(CATEGORY, "Server combat settings. Changes take effect on server restart.");
        ServerTuning tuning = read(fileConfig, registeredItem, warn);
        if (fileConfig.hasChanged()) {
            fileConfig.save();
        }
        return tuning;
    }

    private static ServerTuning read(Configuration config, Predicate<String> registeredItem, Consumer<String> warn) {
        double ordinary = number(config, "ordinarySwitchLead", ORDINARY_LEAD, 0, Double.MAX_VALUE, false,
                "Fraction a challenger must exceed the current threat by (0.2 means 20%).", warn);
        double maximum = number(config, "maximumChaseLead", MAXIMUM_LEAD, 0, Double.MAX_VALUE, false,
                "Fraction required at the far end of a chase. At least ordinarySwitchLead.", warn);
        if (maximum < ordinary) {
            warn.accept("maximumChaseLead=" + maximum + " is below ordinarySwitchLead=" + ordinary
                    + "; using ordinarySwitchLead=" + ORDINARY_LEAD + ", maximumChaseLead=" + MAXIMUM_LEAD);
            ordinary = ORDINARY_LEAD;
            maximum = MAXIMUM_LEAD;
        }
        double start = number(config, "chaseIncreaseStart", CHASE_START, 0, Double.MAX_VALUE, true,
                "Distance in blocks where the extra chase lead begins. Positive.", warn);
        double end = number(config, "chaseIncreaseEnd", CHASE_END, 0, Double.MAX_VALUE, true,
                "Distance in blocks where the maximum chase lead applies. Greater than chaseIncreaseStart.", warn);
        if (end <= start) {
            warn.accept("chaseIncreaseEnd=" + end + " is not above chaseIncreaseStart=" + start
                    + "; using chaseIncreaseStart=" + CHASE_START + ", chaseIncreaseEnd=" + CHASE_END);
            start = CHASE_START;
            end = CHASE_END;
        }
        double opening = number(config, "openingThreat", OPENING_THREAT, 0, Rules.MAX_SCORE, false,
                "Threat seeded for a mob's eligible current target when tracking starts.", warn);
        int taunt = duration(config, "tauntDuration", TAUNT_SECONDS, true, "How long a default taunt holds aggro.", warn);
        int reset = duration(config, "noEligibleResetDelay", RESET_SECONDS, false,
                "How long a mob keeps scores with no eligible target.", warn);
        int sight = duration(config, "sightLossGrace", SIGHT_SECONDS, false,
                "How long a mob retains its target after losing sight.", warn);

        Set<MobType> enabled = readMobCoverage(config, warn);
        Set<String> blacklist = mobIds(config, "mobBlacklist",
                "Exact entity IDs to exclude from Threatworks control, e.g. minecraft:zombie.", warn);
        Map<MobType, Double> seeds = new EnumMap<>(MobType.class);
        for (String entry : list(config, "openingThreatOverrides", new String[0],
                "Replace opening threat for one mob, e.g. minecraft:zombie=15.", warn)) {
            String[] parts = entry.split("=", -1);
            MobType type = MobType.find(parts[0].trim());
            double amount = parts.length == 2 ? parse(parts[1]) : Double.NaN;
            if (type == null || !inRange(amount, 0, Rules.MAX_SCORE, false)) {
                warn.accept("openingThreatOverrides entry '" + entry + "' is invalid; "
                        + (type == null ? "ignoring it" : "using " + seeds.getOrDefault(type, opening)));
            } else if (seeds.putIfAbsent(type, amount) != null) {
                warn.accept("Duplicate openingThreatOverrides entry '" + entry + "'; keeping " + seeds.get(type));
            }
        }
        Map<MobType, Rules> rules = new EnumMap<>(MobType.class);
        for (MobType type : MobType.values()) {
            rules.put(type, new Rules(seeds.getOrDefault(type, opening), ordinary, maximum, start, end, taunt, reset, sight));
        }
        Map<String, Map<Integer, Double>> assignments = new HashMap<>();
        List<Runnable> invalidValues = new ArrayList<>();
        for (String entry : list(config, "itemThreatPointAssignments", new String[0],
                "Signed points per equipped item: minecraft:stick=100 or minecraft:wool@14=-50. Zero overrides a provider.", warn)) {
            String[] parts = entry.split("=", -1);
            String[] selector = parts[0].trim().split("@", -1);
            String item = selector[0];
            Integer metadata = selector.length == 2 ? metadata(selector[1]) : null;
            double points = parts.length == 2 ? parse(parts[1]) : Double.NaN;
            if (selector.length > 2 || (selector.length == 2 && metadata == null)
                    || !NAMESPACED_ID.matcher(item).matches() || !registeredItem.test(item)) {
                warn.accept("Invalid itemThreatPointAssignments selector in '" + entry + "'; ignoring it");
                continue;
            }
            if (!validItemPoints(points)) {
                // Resolve diagnostics after parsing so valid fallbacks can appear anywhere in the list.
                invalidValues.add(() -> {
                    Map<Integer, Double> variants = assignments.get(item);
                    Double effective = variants == null ? null : variants.get(metadata);
                    if (effective == null && variants != null) {
                        effective = variants.get(null);
                    }
                    warn.accept("Invalid itemThreatPointAssignments points in '" + entry + "'; using "
                            + (effective != null ? "pack value " + effective : "the item provider, or zero if absent"));
                });
                continue;
            }
            Map<Integer, Double> variants = assignments.computeIfAbsent(item, ignored -> new HashMap<>());
            Double previous = variants.putIfAbsent(metadata, points);
            if (previous != null) {
                warn.accept("Duplicate itemThreatPointAssignments entry '" + entry + "'; keeping " + previous);
            }
        }
        invalidValues.forEach(Runnable::run);
        return new ServerTuning(enabled, blacklist, rules, assignments);
    }

    private static Set<MobType> readMobCoverage(Configuration config, Consumer<String> warn) {
        boolean hasLegacyList = config.hasKey(CATEGORY, "enabledMobs");
        String fallback = hasLegacyList ? "legacy" : "expanded";
        Property selection = config.get(CATEGORY, "mobCoverage", fallback,
                "Use expanded for current defaults and mobOptIn, or legacy to keep the old enabledMobs list.");
        String mode = selection.isList() ? "<list>" : selection.getString().trim();
        if (!"legacy".equals(mode) && !"expanded".equals(mode)) {
            warn.accept("mobCoverage=" + mode + " is invalid; using " + fallback);
            mode = fallback;
        }
        Set<String> optIn = mobIds(config, "mobOptIn",
                "Exact IDs of verified opt-in mobs in expanded mode, e.g. specialmobs:specialskeleton.", warn);
        if ("expanded".equals(mode)) {
            Set<MobType> enabled = EnumSet.noneOf(MobType.class);
            for (MobType type : MobType.values()) {
                if (type.defaultEnabled) {
                    enabled.add(type);
                }
            }
            for (String id : optIn) {
                MobType type = MobType.find(id);
                if (type == null) {
                    warn.accept("mobOptIn entry '" + id + "' has no supported mob policy; ignoring it");
                } else {
                    enabled.add(type);
                }
            }
            return enabled;
        }

        Set<MobType> enabled = EnumSet.noneOf(MobType.class);
        String[] legacyDefaults = {MobType.ZOMBIE.id, MobType.SKELETON.id};
        for (String value : list(config, "enabledMobs", legacyDefaults,
                "Legacy allowlist for minecraft:zombie and minecraft:skeleton. Set mobCoverage=expanded to migrate.", warn)) {
            MobType type = MobType.find(value.trim());
            if (type == MobType.ZOMBIE || type == MobType.SKELETON) {
                enabled.add(type);
            } else {
                warn.accept("enabledMobs entry '" + value + "' has no legacy mob policy; ignoring it");
            }
        }
        return enabled;
    }

    private static Set<String> mobIds(Configuration config, String key, String comment, Consumer<String> warn) {
        Set<String> ids = new LinkedHashSet<>();
        Set<String> malformed = new HashSet<>();
        for (String value : list(config, key, new String[0], comment, warn)) {
            String id = value.trim();
            if (NAMESPACED_ID.matcher(id).matches()) {
                ids.add(id);
            } else if (malformed.add(id)) {
                warn.accept(key + " entry '" + id + "' is not an exact namespaced entity ID; ignoring it");
            }
        }
        return ids;
    }

    public static boolean validItemPoints(double points) {
        return Double.isFinite(points) && Math.abs(points) <= MAX_ITEM_POINTS;
    }

    private static Integer metadata(String value) {
        try {
            int number = Integer.parseInt(value);
            return number >= 0 ? number : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int duration(Configuration config, String key, double fallback, boolean positive, String comment,
                                Consumer<String> warn) {
        return (int) Math.ceil(number(config, key, fallback, 0, MAX_SECONDS, positive,
                comment + " Seconds, rounded up to a server tick.", warn) * TICKS_PER_SECOND);
    }

    private static double number(Configuration config, String key, double fallback, double min, double max,
                                 boolean exclusive, String comment, Consumer<String> warn) {
        Property property = config.get(CATEGORY, key, Double.toString(fallback), comment, Property.Type.DOUBLE);
        double number = property.isList() ? Double.NaN : parse(property.getString());
        if (inRange(number, min, max, exclusive)) {
            return number;
        }
        warn.accept(key + "=" + (property.isList() ? "<list>" : property.getString()) + " is invalid; using " + fallback);
        return fallback;
    }

    private static boolean inRange(double value, double min, double max, boolean exclusive) {
        return Double.isFinite(value) && (exclusive ? value > min : value >= min) && value <= max;
    }

    private static double parse(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return Double.NaN;
        }
    }

    private static String[] list(Configuration config, String key, String[] defaults, String comment,
                                 Consumer<String> warn) {
        Property property = config.get(CATEGORY, key, defaults, comment);
        if (property.isList()) {
            return property.getStringList();
        }
        warn.accept(key + " requires a list; using " + Arrays.toString(defaults));
        return defaults;
    }
}

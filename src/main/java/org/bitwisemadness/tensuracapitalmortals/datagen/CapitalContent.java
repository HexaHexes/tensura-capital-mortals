package org.bitwisemadness.tensuracapitalmortals.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The datapack's JSON tables: one jigsaw structure, structure set, start /
 * streets / buildings template pools and capital tag anchor per faction
 * (humans, elves, dwarves), ported from the hand-authored originals
 * (issue #19).
 *
 * <p>The salts keep the three capitals on independent random-spread lattices
 * (777001..777003); the biome lists pick the races' home terrain; the pools
 * wire the {@link CapitalTemplates} jigsaw pieces together via the uniform
 * road connection.</p>
 */
public final class CapitalContent {

    /** The factions in id order - the salts and biome lists key off this. */
    public static final List<String> FACTIONS =
            List.of("humans", "elves", "dwarves");

    private static final Map<String, List<String>> BIOMES = Map.of(
            "humans", List.of("minecraft:plains", "minecraft:sunflower_plains",
                    "minecraft:forest", "minecraft:birch_forest",
                    "minecraft:flower_forest", "minecraft:meadow"),
            "elves", List.of("minecraft:forest", "minecraft:birch_forest",
                    "minecraft:dark_forest", "minecraft:flower_forest",
                    "minecraft:old_growth_birch_forest", "tensura:ancient_forest"),
            "dwarves", List.of("minecraft:meadow", "minecraft:cherry_grove",
                    "minecraft:savanna_plateau", "minecraft:snowy_slopes",
                    "minecraft:windswept_hills", "minecraft:grove"));

    private static final Map<String, Integer> SALTS = Map.of(
            "humans", 777001, "elves", 777002, "dwarves", 777003);

    private static final int MAX_DISTANCE = 116;
    private static final int SPACING = 256;
    private static final int SEPARATION = 8;

    private CapitalContent() {
    }

    /** Every datapack JSON, keyed by its path inside the jar. */
    public static Map<String, JsonObject> files() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        for (String faction : FACTIONS) {
            files.put(structurePath(faction), structure(faction));
            files.put(structureSetPath(faction), structureSet(faction));
            files.put(poolPath(faction, "start"), startPool(faction));
            files.put(poolPath(faction, "streets"), streetsPool(faction));
            files.put(poolPath(faction, "buildings"), buildingsPool(faction));
            files.put(capitalTagPath(faction), capitalTag(faction));
        }
        validate(files);
        return files;
    }

    private static String structurePath(String faction) {
        return "data/tensura_capital_mortals/worldgen/structure/" + faction + ".json";
    }

    private static String structureSetPath(String faction) {
        return "data/tensura_capital_mortals/worldgen/structure_set/" + faction + ".json";
    }

    private static String poolPath(String faction, String kind) {
        return "data/tensura_capital_mortals/worldgen/template_pool/" + faction + "/" + kind + ".json";
    }

    private static String capitalTagPath(String faction) {
        return "data/tensura_faction_spawn/tags/worldgen/structure/capital/" + faction + ".json";
    }

    private static JsonObject structure(String faction) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:jigsaw");
        JsonArray biomes = new JsonArray();
        BIOMES.get(faction).forEach(biomes::add);
        json.add("biomes", biomes);
        json.addProperty("max_distance_from_center", MAX_DISTANCE);
        json.addProperty("project_start_to_heightmap", "WORLD_SURFACE_WG");
        json.addProperty("size", 10);
        json.add("spawn_overrides", new JsonObject());
        JsonObject startHeight = new JsonObject();
        startHeight.addProperty("absolute", 0);
        json.add("start_height", startHeight);
        json.addProperty("start_pool", "tensura_capital_mortals:" + faction + "/start");
        json.addProperty("step", "surface_structures");
        json.addProperty("terrain_adaptation", "beard_thin");
        json.addProperty("use_expansion_hack", false);
        return json;
    }

    private static JsonObject structureSet(String faction) {
        JsonObject placement = new JsonObject();
        placement.addProperty("type", "minecraft:random_spread");
        placement.addProperty("salt", SALTS.get(faction));
        placement.addProperty("separation", SEPARATION);
        placement.addProperty("spacing", SPACING);
        JsonObject element = new JsonObject();
        element.addProperty("structure", "tensura_capital_mortals:" + faction);
        element.addProperty("weight", 1);
        JsonArray structures = new JsonArray();
        structures.add(element);
        JsonObject json = new JsonObject();
        json.add("placement", placement);
        json.add("structures", structures);
        return json;
    }

    /** A pool piece: the template id below the faction folder plus its weight. */
    private record Piece(String name, int weight) {
    }

    private static JsonObject pool(List<Piece> pieces) {
        JsonArray elements = new JsonArray();
        for (Piece piece : pieces) {
            JsonObject element = new JsonObject();
            element.addProperty("element_type", "minecraft:single_pool_element");
            element.addProperty("location", "tensura_capital_mortals:" + piece.name());
            element.addProperty("processors", "minecraft:empty");
            element.addProperty("projection", "rigid");
            JsonObject entry = new JsonObject();
            entry.addProperty("weight", piece.weight());
            entry.add("element", element);
            elements.add(entry);
        }
        JsonObject json = new JsonObject();
        json.addProperty("fallback", "minecraft:empty");
        json.add("elements", elements);
        return json;
    }

    /** The race's dwelling piece: house, hut or hall. */
    private static String home(String faction) {
        return switch (faction) {
            case "humans" -> "house";
            case "elves" -> "hut";
            default -> "hall";
        };
    }

    /** The race's landmark piece: keep or treetop. */
    private static String landmark(String faction) {
        return faction.equals("elves") ? "treetop" : "keep";
    }

    private static JsonObject startPool(String faction) {
        return pool(List.of(new Piece(faction + "/start", 1)));
    }

    private static JsonObject streetsPool(String faction) {
        String f = faction;
        return pool(List.of(new Piece(f + "/street", 2),
                new Piece(f + "/" + home(f), 2), new Piece(f + "/" + landmark(f), 1)));
    }

    private static JsonObject buildingsPool(String faction) {
        String f = faction;
        return pool(List.of(new Piece(f + "/" + home(f), 3),
                new Piece(f + "/" + landmark(f), 1)));
    }

    private static JsonObject capitalTag(String faction) {
        JsonObject json = new JsonObject();
        json.addProperty("replace", false);
        JsonArray values = new JsonArray();
        values.add("tensura_capital_mortals:" + faction);
        json.add("values", values);
        return json;
    }

    /**
     * Pins the tables against silent drift: counts, the salts, the start-pool
     * wiring, the pool ladders and every template cross-reference the shipped
     * jigsaw graph needs.
     */
    private static void validate(Map<String, JsonObject> files) {
        List<String> errors = new ArrayList<>();
        if (files.size() != FACTIONS.size() * 6) {
            errors.add("file count: " + files.size() + " expected " + (FACTIONS.size() * 6));
        }
        for (String faction : FACTIONS) {
            int salt = files.get(structureSetPath(faction)).getAsJsonObject("placement")
                    .get("salt").getAsInt();
            if (salt != SALTS.get(faction)) {
                errors.add(faction + " structure_set salt drifted: " + salt);
            }
            JsonObject structure = files.get(structurePath(faction));
            if (!structure.get("start_pool").getAsString()
                    .equals("tensura_capital_mortals:" + faction + "/start")) {
                errors.add(faction + " structure start_pool drifted");
            }
            if (files.get(poolPath(faction, "start")).getAsJsonArray("elements").size() != 1) {
                errors.add(faction + " start pool must carry exactly the start piece");
            }
            JsonObject tag = files.get(capitalTagPath(faction));
            if (tag.get("replace").getAsBoolean()) {
                errors.add(faction + " capital tag: replace must stay false");
            }
            // Every pool element must point at a template the generator builds.
            for (String kind : List.of("start", "streets", "buildings")) {
                for (var element : files.get(poolPath(faction, kind)).getAsJsonArray("elements")) {
                    String location = element.getAsJsonObject().getAsJsonObject("element")
                            .get("location").getAsString();
                    String piece = location.substring(location.lastIndexOf('/') + 1);
                    if (!CapitalTemplates.exists(faction, piece)) {
                        errors.add(kind + " pool references unbuilt template " + location);
                    }
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("capital datapack drift: " + errors);
        }
    }
}

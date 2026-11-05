package org.bitwisemadness.tensuracapitalmortals.datagen;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPOutputStream;

/**
 * The twelve jigsaw template pieces (start, street, dwellings per faction),
 * ported verbatim from the retired {@code tools/gen_templates.py} (issue
 * #19). Every piece is a small voxel model; the town grows by jigsaw
 * expansion through the uniform "road" connection, never by template size -
 * a single jigsaw structure is capped at 128 blocks from its center.
 *
 * <p>The NBT is written by a hand-rolled encoder mirroring the script's
 * layout byte-for-byte (gzip container aside, whose header differs between
 * runtimes): size, entities, blocks (pos-sorted), palette (first-appearance
 * order, properties sorted), DataVersion 3955 = 1.21.1. Deterministic, so
 * the committed templates are stable under the drift gate.</p>
 */
public final class CapitalTemplates {

    private static final int DATA_VERSION = 3955;
    private static final String NAMESPACE = "tensura_capital_mortals";

    private CapitalTemplates() {
    }

    /** Whether the generator builds this faction piece (for validation). */
    public static boolean exists(String faction, String piece) {
        return builders().containsKey(faction + "/" + piece);
    }

    /** Every template, keyed by its path inside the jar. */
    public static Map<String, byte[]> files() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        builders().forEach((name, builder) -> files.put(
                "data/" + NAMESPACE + "/structure/" + name + ".nbt", builder.build().nbt()));
        return files;
    }

    private interface Builder {
        Template build();
    }

    private static Map<String, Builder> builders() {
        Map<String, Builder> builders = new LinkedHashMap<>();
        builders.put("humans/start", CapitalTemplates::humansStart);
        builders.put("humans/street", CapitalTemplates::humansStreet);
        builders.put("humans/house", CapitalTemplates::humansHouse);
        builders.put("humans/keep", CapitalTemplates::humansKeep);
        builders.put("elves/start", CapitalTemplates::elvesStart);
        builders.put("elves/street", CapitalTemplates::elvesStreet);
        builders.put("elves/treetop", CapitalTemplates::elvesTreetop);
        builders.put("elves/hut", CapitalTemplates::elvesHut);
        builders.put("dwarves/start", CapitalTemplates::dwarvesStart);
        builders.put("dwarves/street", CapitalTemplates::dwarvesStreet);
        builders.put("dwarves/hall", CapitalTemplates::dwarvesHall);
        builders.put("dwarves/keep", CapitalTemplates::dwarvesKeep);
        return Map.copyOf(builders);
    }

    /** A palette entry: a block id plus optional sorted properties. */
    private record State(String name, Map<String, String> props) {
    }

    /** One voxel model: blocks, deduplicated palette and jigsaw anchors. */
    private static final class Template {
        private final int[] size;
        private final List<State> palette = new ArrayList<>();
        private final Map<State, Integer> paletteIndex = new HashMap<>();
        private final TreeMap<long[], Integer> blocks = new TreeMap<>(
                (a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0])
                        : a[1] != b[1] ? Long.compare(a[1], b[1])
                        : Long.compare(a[2], b[2]));
        private final Map<String, String> jigsaws = new HashMap<>();

        private Template(int width, int height, int depth) {
            this.size = new int[] {width, height, depth};
        }

        private int state(String name, String... props) {
            Map<String, String> sorted = new TreeMap<>();
            for (int i = 0; i < props.length; i += 2) {
                sorted.put(props[i], props[i + 1]);
            }
            State state = new State(name, sorted);
            return paletteIndex.computeIfAbsent(state, key -> {
                palette.add(key);
                return palette.size() - 1;
            });
        }

        private void set(int x, int y, int z, String name, String... props) {
            if (x >= 0 && x < size[0] && y >= 0 && y < size[1] && z >= 0 && z < size[2]) {
                blocks.put(new long[] {x, y, z}, state(name, props));
            }
        }

        private void setIfAbsent(int x, int y, int z, String name, String... props) {
            if (inBounds(x, y, z) && !blocks.containsKey(new long[] {x, y, z})) {
                set(x, y, z, name, props);
            }
        }

        private boolean inBounds(int x, int y, int z) {
            return x >= 0 && x < size[0] && y >= 0 && y < size[1] && z >= 0 && z < size[2];
        }

        private void fill(int x0, int y0, int z0, int x1, int y1, int z1, String name, String... props) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                    for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                        set(x, y, z, name, props);
                    }
                }
            }
        }

        private void walls(int x0, int z0, int x1, int z1, int y0, int y1, String name, String... props) {
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    set(x, y, z0, name, props);
                    set(x, y, z1, name, props);
                }
                for (int z = z0; z <= z1; z++) {
                    set(x0, y, z, name, props);
                    set(x1, y, z, name, props);
                }
            }
        }

        private static String jigsawKey(int x, int y, int z) {
            return x + "," + y + "," + z;
        }

        private void jigsaw(int x, int y, int z, String orientation, String pool) {
            set(x, y, z, "minecraft:jigsaw", "orientation", orientation);
            jigsaws.put(jigsawKey(x, y, z), pool);
        }

        /** Mirrors the script's to_nbt + gzip(mtime=0) byte layout. */
        private byte[] nbt() {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            NbtWriter out = new NbtWriter(raw);
            List<Field> fields = new ArrayList<>();
            fields.add(new Field(Field.TAG_LIST, "size",
                    intListPayload(size[0], size[1], size[2])));
            fields.add(new Field(Field.TAG_LIST, "entities",
                    typedListPayload(Field.TAG_COMPOUND, List.of())));
            List<byte[]> blockPayloads = new ArrayList<>();
            for (Map.Entry<long[], Integer> entry : blocks.entrySet()) {
                long[] pos = entry.getKey();
                List<Field> block = new ArrayList<>();
                block.add(new Field(Field.TAG_LIST, "pos",
                        intListPayload((int) pos[0], (int) pos[1], (int) pos[2])));
                block.add(new Field(Field.TAG_INT, "state", int32(entry.getValue())));
                String pool = jigsaws.get(jigsawKey((int) pos[0], (int) pos[1], (int) pos[2]));
                if (pool != null) {
                    block.add(new Field(Field.TAG_COMPOUND, "nbt", compoundPayload(List.of(
                            Field.str("name", "road"),
                            Field.str("target", "road"),
                            Field.str("pool", pool),
                            Field.str("final_state", "minecraft:air"),
                            Field.str("joint", "roll")))));
                }
                blockPayloads.add(compoundPayload(block));
            }
            fields.add(new Field(Field.TAG_LIST, "blocks",
                    typedListPayload(Field.TAG_COMPOUND, blockPayloads)));
            List<byte[]> palettePayloads = new ArrayList<>();
            for (State state : palette) {
                List<Field> entry = new ArrayList<>();
                entry.add(Field.str("Name", state.name()));
                if (!state.props().isEmpty()) {
                    List<Field> props = new ArrayList<>();
                    state.props().forEach((key, value) ->
                            props.add(Field.str(key, value)));
                    entry.add(new Field(Field.TAG_COMPOUND, "Properties",
                            compoundPayload(props)));
                }
                palettePayloads.add(compoundPayload(entry));
            }
            fields.add(new Field(Field.TAG_LIST, "palette",
                    typedListPayload(Field.TAG_COMPOUND, palettePayloads)));
            fields.add(new Field(Field.TAG_INT, "DataVersion", int32(DATA_VERSION)));
            out.rootCompound(fields);
            return gzip(raw.toByteArray());
        }
    }

    // --- the Humans capital pieces -------------------------------------------

    private static final String BRICK = "minecraft:stone_bricks";
    private static final String COBBLE = "minecraft:cobblestone";
    private static final String PLANKS = "minecraft:oak_planks";
    private static final String LOG = "minecraft:oak_log";
    private static final String GLASS = "minecraft:glass";
    private static final String ANDESITE = "minecraft:andesite";
    private static final String MOSS = "minecraft:moss_block";
    private static final String LEAVES = "minecraft:oak_leaves";

    private static Template humansStart() {
        Template t = new Template(16, 8, 16);
        t.fill(0, 0, 0, 15, 0, 15, COBBLE);
        for (int i = 0; i < 16; i++) {
            t.set(i, 0, 0, BRICK);
            t.set(i, 0, 15, BRICK);
            t.set(0, 0, i, BRICK);
            t.set(15, 0, i, BRICK);
        }
        t.fill(0, 1, 0, 15, 5, 0, BRICK);
        t.fill(6, 1, 0, 9, 4, 0, "minecraft:air");
        t.fill(5, 5, 0, 10, 5, 0, BRICK);
        t.fill(0, 1, 0, 1, 7, 1, BRICK);
        t.fill(14, 1, 0, 15, 7, 1, BRICK);
        t.jigsaw(7, 1, 0, "south_up", NAMESPACE + ":humans/streets");
        t.jigsaw(8, 1, 15, "north_up", NAMESPACE + ":humans/streets");
        t.jigsaw(15, 1, 8, "east_up", NAMESPACE + ":humans/streets");
        t.jigsaw(0, 1, 8, "west_up", NAMESPACE + ":humans/streets");
        return t;
    }

    private static Template humansStreet() {
        Template t = new Template(16, 4, 16);
        t.fill(6, 0, 0, 9, 0, 15, COBBLE);
        t.jigsaw(7, 1, 0, "south_up", NAMESPACE + ":humans/streets");
        t.jigsaw(7, 1, 15, "north_up", NAMESPACE + ":humans/streets");
        t.jigsaw(0, 1, 7, "west_up", NAMESPACE + ":humans/buildings");
        t.jigsaw(15, 1, 7, "east_up", NAMESPACE + ":humans/buildings");
        return t;
    }

    private static Template humansHouse() {
        Template t = new Template(9, 7, 9);
        t.fill(0, 0, 0, 8, 0, 8, PLANKS);
        t.walls(0, 0, 8, 8, 1, 3, PLANKS);
        for (int[] corner : new int[][] {{0, 0}, {8, 0}, {0, 8}, {8, 8}}) {
            t.fill(corner[0], 1, corner[1], corner[0], 3, corner[1], LOG);
        }
        t.set(4, 2, 8, GLASS);
        t.set(0, 2, 4, GLASS);
        t.set(8, 2, 4, GLASS);
        t.fill(4, 1, 0, 4, 2, 0, "minecraft:air");
        t.fill(0, 4, 0, 8, 4, 8, PLANKS);
        t.jigsaw(4, 1, 0, "south_up", NAMESPACE + ":humans/buildings");
        return t;
    }

    private static Template humansKeep() {
        Template t = new Template(11, 16, 11);
        t.fill(0, 0, 0, 10, 0, 10, COBBLE);
        t.walls(0, 0, 10, 10, 1, 9, BRICK);
        for (int z = 1; z < 10; z++) {
            if (z % 2 == 0) {
                t.set(0, 4, z, GLASS);
                t.set(10, 4, z, GLASS);
            }
        }
        for (int x = 1; x < 10; x++) {
            if (x % 2 == 0) {
                t.set(x, 4, 0, GLASS);
                t.set(x, 4, 10, GLASS);
            }
        }
        t.fill(5, 1, 0, 5, 2, 0, "minecraft:air");
        t.fill(0, 10, 0, 10, 10, 10, BRICK);
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 10; z++) {
                if ((x == 0 || x == 10 || z == 0 || z == 10) && (x + z) % 2 == 0) {
                    t.set(x, 11, z, BRICK);
                }
            }
        }
        t.jigsaw(5, 1, 0, "south_up", NAMESPACE + ":humans/buildings");
        return t;
    }

    // --- the Elves capital pieces --------------------------------------------

    private static Template elvesStart() {
        Template t = new Template(16, 13, 16);
        t.fill(0, 0, 0, 15, 0, 15, MOSS);
        t.fill(7, 1, 7, 9, 9, 9, LOG);
        for (int y = 6; y < 13; y++) {
            int radius = 6 - (y - 6) / 2;
            for (int x = 8 - radius; x < 9 + radius; x++) {
                for (int z = 8 - radius; z < 9 + radius; z++) {
                    boolean trunkColumn = x == 8 && z == 8 && y <= 9;
                    if (!trunkColumn) {
                        t.setIfAbsent(x, y, z, LEAVES, "persistent", "true");
                    }
                }
            }
        }
        for (int i = 0; i < 16; i++) {
            t.set(i, 0, 0, BRICK);
            t.set(i, 0, 15, BRICK);
            t.set(0, 0, i, BRICK);
            t.set(15, 0, i, BRICK);
        }
        t.jigsaw(7, 1, 0, "south_up", NAMESPACE + ":elves/streets");
        t.jigsaw(8, 1, 15, "north_up", NAMESPACE + ":elves/streets");
        t.jigsaw(15, 1, 8, "east_up", NAMESPACE + ":elves/streets");
        t.jigsaw(0, 1, 8, "west_up", NAMESPACE + ":elves/streets");
        return t;
    }

    private static Template elvesStreet() {
        Template t = new Template(16, 4, 16);
        t.fill(6, 0, 0, 9, 0, 15, MOSS);
        t.jigsaw(7, 1, 0, "south_up", NAMESPACE + ":elves/streets");
        t.jigsaw(7, 1, 15, "north_up", NAMESPACE + ":elves/streets");
        t.jigsaw(0, 1, 7, "west_up", NAMESPACE + ":elves/buildings");
        t.jigsaw(15, 1, 7, "east_up", NAMESPACE + ":elves/buildings");
        return t;
    }

    private static Template elvesTreetop() {
        Template t = new Template(9, 13, 9);
        t.fill(4, 0, 4, 4, 6, 4, LOG);
        t.fill(2, 7, 2, 6, 7, 6, PLANKS);
        t.walls(2, 2, 6, 6, 8, 9, PLANKS);
        t.fill(4, 8, 2, 4, 9, 2, "minecraft:air");
        t.set(2, 9, 4, GLASS);
        t.set(6, 9, 4, GLASS);
        t.fill(1, 10, 1, 7, 10, 7, LEAVES, "persistent", "true");
        t.fill(3, 11, 3, 5, 11, 5, LEAVES, "persistent", "true");
        t.jigsaw(4, 1, 3, "south_up", NAMESPACE + ":elves/buildings");
        return t;
    }

    private static Template elvesHut() {
        Template t = new Template(7, 6, 7);
        t.fill(0, 0, 0, 6, 0, 6, MOSS);
        t.walls(0, 0, 6, 6, 1, 2, PLANKS);
        for (int[] corner : new int[][] {{0, 0}, {6, 0}, {0, 6}, {6, 6}}) {
            t.fill(corner[0], 1, corner[1], corner[0], 2, corner[1], LOG);
        }
        t.fill(3, 1, 0, 3, 2, 0, "minecraft:air");
        t.fill(0, 3, 0, 6, 3, 6, LEAVES, "persistent", "true");
        t.fill(1, 4, 1, 5, 4, 5, LEAVES, "persistent", "true");
        t.jigsaw(3, 1, 0, "south_up", NAMESPACE + ":elves/buildings");
        return t;
    }

    // --- the Dwarves capital pieces ------------------------------------------

    private static String rock(int x, int z) {
        int mix = (x + z) % 3;
        return mix == 0 ? ANDESITE : mix == 1 ? COBBLE : BRICK;
    }

    private static Template dwarvesStart() {
        Template t = new Template(20, 16, 16);
        t.fill(0, 0, 0, 19, 0, 15, COBBLE);
        t.fill(0, 1, 0, 19, 6, 0, BRICK);
        t.fill(8, 1, 0, 11, 4, 0, "minecraft:air");
        t.fill(8, 5, 0, 11, 6, 0, BRICK);
        for (int[] tower : new int[][] {{0, 0}, {1, 0}, {18, 0}, {19, 0}}) {
            t.fill(tower[0], 1, tower[1], tower[0], 8, tower[1], BRICK);
        }
        t.walls(0, 0, 19, 8, 1, 5, BRICK);
        for (int z = 8; z < 16; z++) {
            int top = 5 + (z - 8);
            for (int x = 0; x < 20; x++) {
                for (int y = 1; y <= top; y++) {
                    t.set(x, y, z, rock(x, z));
                }
            }
        }
        t.fill(9, 1, 12, 10, 3, 15, "minecraft:air");
        t.jigsaw(9, 1, 0, "south_up", NAMESPACE + ":dwarves/streets");
        t.jigsaw(9, 1, 15, "north_up", NAMESPACE + ":dwarves/streets");
        t.jigsaw(19, 1, 8, "east_up", NAMESPACE + ":dwarves/streets");
        t.jigsaw(0, 1, 8, "west_up", NAMESPACE + ":dwarves/streets");
        return t;
    }

    private static Template dwarvesStreet() {
        Template t = new Template(16, 4, 16);
        t.fill(6, 0, 0, 9, 0, 15, BRICK);
        t.jigsaw(7, 1, 0, "south_up", NAMESPACE + ":dwarves/streets");
        t.jigsaw(7, 1, 15, "north_up", NAMESPACE + ":dwarves/streets");
        t.jigsaw(0, 1, 7, "west_up", NAMESPACE + ":dwarves/buildings");
        t.jigsaw(15, 1, 7, "east_up", NAMESPACE + ":dwarves/buildings");
        return t;
    }

    private static Template dwarvesHall() {
        Template t = new Template(13, 8, 13);
        t.fill(0, 0, 0, 12, 0, 12, COBBLE);
        t.walls(0, 0, 12, 12, 1, 5, BRICK);
        for (int z = 1; z < 12; z++) {
            if (z % 2 == 0) {
                t.set(0, 4, z, GLASS);
                t.set(12, 4, z, GLASS);
            }
        }
        t.fill(6, 1, 0, 6, 2, 0, "minecraft:air");
        t.fill(0, 6, 0, 12, 6, 12, BRICK);
        t.walls(0, 0, 12, 12, 7, 7, BRICK);
        t.jigsaw(6, 1, 0, "south_up", NAMESPACE + ":dwarves/buildings");
        return t;
    }

    private static Template dwarvesKeep() {
        Template t = new Template(13, 18, 13);
        t.fill(0, 0, 0, 12, 0, 12, COBBLE);
        t.walls(0, 0, 12, 12, 1, 11, BRICK);
        for (int z = 1; z < 12; z++) {
            if (z % 2 == 0) {
                t.set(0, 5, z, GLASS);
                t.set(12, 5, z, GLASS);
            }
        }
        for (int x = 1; x < 12; x++) {
            if (x % 2 == 0) {
                t.set(x, 5, 0, GLASS);
                t.set(x, 5, 12, GLASS);
            }
        }
        t.fill(6, 1, 0, 6, 2, 0, "minecraft:air");
        t.fill(0, 12, 0, 12, 12, 12, BRICK);
        for (int x = 0; x <= 12; x++) {
            for (int z = 0; z <= 12; z++) {
                if ((x == 0 || x == 12 || z == 0 || z == 12) && (x + z) % 2 == 0) {
                    t.set(x, 13, z, BRICK);
                }
            }
        }
        t.jigsaw(6, 1, 0, "south_up", NAMESPACE + ":dwarves/buildings");
        return t;
    }

    // --- minimal NBT writer (mirrors gen_templates.py's encoder) --------------

    private record Field(int tag, String name, byte[] payload) {
        static final int TAG_INT = 3;
        static final int TAG_STRING = 8;
        static final int TAG_LIST = 9;
        static final int TAG_COMPOUND = 10;

        private static Field str(String name, String value) {
            byte[] raw = value.getBytes(StandardCharsets.UTF_8);
            byte[] payload = new byte[2 + raw.length];
            payload[0] = (byte) (raw.length >> 8);
            payload[1] = (byte) raw.length;
            System.arraycopy(raw, 0, payload, 2, raw.length);
            return new Field(TAG_STRING, name, payload);
        }
    }

    private static byte[] int32(int value) {
        return new byte[] {(byte) (value >> 24), (byte) (value >> 16),
                (byte) (value >> 8), (byte) value};
    }

    /** A TAG_List of TAG_Int payload: element type, count, values. */
    private static byte[] intListPayload(int... values) {
        byte[] out = new byte[5 + 4 * values.length];
        out[0] = Field.TAG_INT;
        out[1] = (byte) (values.length >> 24);
        out[2] = (byte) (values.length >> 16);
        out[3] = (byte) (values.length >> 8);
        out[4] = (byte) values.length;
        for (int i = 0; i < values.length; i++) {
            byte[] v = int32(values[i]);
            System.arraycopy(v, 0, out, 5 + 4 * i, 4);
        }
        return out;
    }

    /** A TAG_List of compounds payload: element type, count, payload-only items. */
    private static byte[] typedListPayload(int elementType, List<byte[]> items) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(elementType);
        byte[] count = int32(items.size());
        out.writeBytes(count);
        items.forEach(out::writeBytes);
        return out.toByteArray();
    }

    /** A TAG_Compound payload: typed named fields plus the end byte. */
    private static byte[] compoundPayload(List<Field> fields) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Field field : fields) {
            out.write(field.tag());
            byte[] name = field.name().getBytes(StandardCharsets.UTF_8);
            out.write((name.length >> 8) & 0xFF);
            out.write(name.length & 0xFF);
            out.writeBytes(name);
            out.writeBytes(field.payload());
        }
        out.write(0);
        return out.toByteArray();
    }

    private static byte[] gzip(byte[] payload) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            GZIPOutputStream gzip = new GZIPOutputStream(out) {
                {
                    def.setLevel(java.util.zip.Deflater.BEST_COMPRESSION);
                }
            };
            gzip.write(payload);
            gzip.close();
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("cannot gzip a template", ex);
        }
    }

    /** Streams the root compound in the script's exact byte layout. */
    private static final class NbtWriter {
        private final ByteArrayOutputStream out;

        private NbtWriter(ByteArrayOutputStream out) {
            this.out = out;
        }

        private void rootCompound(List<Field> fields) {
            out.write(Field.TAG_COMPOUND);
            out.write(0);
            out.write(0);
            out.writeBytes(compoundPayload(fields));
        }
    }
}

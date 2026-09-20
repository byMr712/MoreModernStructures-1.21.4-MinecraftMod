import java.io.*;
import java.util.*;

/**
 * SchemaTools — standalone NBT / schematic toolkit (no dependencies, Java 21).
 *
 * Modes:
 *   info   <file.nbt|file.litematic>            - print structure summary
 *   litem  <in.litematic> <out.nbt> [name]      - convert Litematica -> Minecraft structure .nbt
 *   pack   <out.nbt> <piece1.nbt> [piece2...]   - stack pieces vertically, aligning jigsaw joints,
 *                                                 remove jigsaw blocks and shift origin to 0,0,0
 *
 * Block entity nbt is preserved (x/y/z stripped). Only vanilla block states are written out;
 * unknown/modded entries in litematic palette are reported to stderr.
 */
public class SchemaTools {

    // ---------------------------------------------------------------- NBT

    static final int TAG_END = 0, TAG_BYTE = 1, TAG_SHORT = 2, TAG_INT = 3, TAG_LONG = 4,
            TAG_FLOAT = 5, TAG_DOUBLE = 6, TAG_BYTE_ARRAY = 7, TAG_STRING = 8, TAG_LIST = 9,
            TAG_COMPOUND = 10, TAG_INT_ARRAY = 11, TAG_LONG_ARRAY = 12;

    interface Tag {}
    record ByteT(byte v) implements Tag {}
    record ShortT(short v) implements Tag {}
    record IntT(int v) implements Tag {}
    record LongT(long v) implements Tag {}
    record FloatT(float v) implements Tag {}
    record DoubleT(double v) implements Tag {}
    record ByteArrayT(byte[] v) implements Tag {}
    record StrT(String v) implements Tag {}
    record ListT(int type, List<Tag> v) implements Tag {}
    record IntArrayT(int[] v) implements Tag {}
    record LongArrayT(long[] v) implements Tag {}
    static final class CompoundT implements Tag {
        final LinkedHashMap<String, Tag> map = new LinkedHashMap<>();
    }

    static final class NBCtx {
        byte[] buf; int pos;
        NBCtx(byte[] buf) { this.buf = buf; }
    }

    static Tag readTag(NBCtx c) throws IOException {
        int type = c.buf[c.pos++] & 0xFF;
        if (type == 0) return null;
        return readTagPayload(type, c);
    }

    static Tag readNamedTag(NBCtx c) throws IOException {
        int type = c.buf[c.pos++] & 0xFF;
        if (type == 0) return null;
        readString(c);
        return readTagPayload(type, c);
    }

    static Tag readTagPayload(int type, NBCtx c) throws IOException {
        switch (type) {
            case TAG_BYTE: return new ByteT(c.buf[c.pos++]);
            case TAG_SHORT: return new ShortT(readShort(c));
            case TAG_INT: return new IntT(readInt(c));
            case TAG_LONG: return new LongT(readLong(c));
            case TAG_FLOAT: { byte[] b = readBytes(c, 4); return new FloatT(Float.intBitsToFloat(leInt(b))); }
            case TAG_DOUBLE: { byte[] b = readBytes(c, 8); return new DoubleT(Double.longBitsToDouble(leLong(b))); }
            case TAG_BYTE_ARRAY: { int n = readInt(c); byte[] a = readBytes(c, n); return new ByteArrayT(a); }
            case TAG_STRING: { int n = readShort(c) & 0xFFFF; return new StrT(new String(readBytes(c, n), java.nio.charset.StandardCharsets.UTF_8)); }
            case TAG_LIST: {
                int elem = c.buf[c.pos++] & 0xFF;
                int n = readInt(c);
                List<Tag> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) list.add(readTagPayload(elem, c));
                return new ListT(elem, list);
            }
            case TAG_COMPOUND: {
                CompoundT t = new CompoundT();
                while (true) {
                    int t2 = c.buf[c.pos++] & 0xFF;
                    if (t2 == 0) break;
                    String name = readString(c);
                    t.map.put(name, readTagPayload(t2, c));
                }
                return t;
            }
            case TAG_INT_ARRAY: { int n = readInt(c); byte[] b = readBytes(c, n * 4); int[] a = new int[n]; for (int i = 0; i < n; i++) a[i] = leInt(Arrays.copyOfRange(b, i*4, i*4+4)); return new IntArrayT(a); }
            case TAG_LONG_ARRAY: { int n = readInt(c); byte[] b = readBytes(c, n * 8); long[] a = new long[n]; for (int i = 0; i < n; i++) a[i] = leLong(Arrays.copyOfRange(b, i*8, i*8+8)); return new LongArrayT(a); }
            default: throw new IOException("unknown tag type " + type);
        }
    }

    static String readString(NBCtx c) throws IOException { int n = readShort(c) & 0xFFFF; return new String(readBytes(c, n), java.nio.charset.StandardCharsets.UTF_8); }
    static byte[] readBytes(NBCtx c, int n) throws IOException { if (c.pos + n > c.buf.length) throw new EOFException("short read"); byte[] out = Arrays.copyOfRange(c.buf, c.pos, c.pos + n); c.pos += n; return out; }
    static short readShort(NBCtx c) throws IOException { int a = c.buf[c.pos++] & 0xFF, b = c.buf[c.pos++] & 0xFF; return (short)((a << 8) | b); }
    static int readInt(NBCtx c) throws IOException { int a = c.buf[c.pos++] & 0xFF, b = c.buf[c.pos++] & 0xFF, d = c.buf[c.pos++] & 0xFF, e = c.buf[c.pos++] & 0xFF; return (a << 24) | (b << 16) | (d << 8) | e; }
    static long readLong(NBCtx c) throws IOException { long v = 0; for (int i = 0; i < 8; i++) v = (v << 8) | (c.buf[c.pos++] & 0xFF); return v; }
    static int leInt(byte[] b) { return (b[0] & 0xFF) | ((b[1] & 0xFF) << 8) | ((b[2] & 0xFF) << 16) | ((b[3] & 0xFF) << 24); }
    static long leLong(byte[] b) { long v = 0; for (int i = 7; i >= 0; i--) v = (v << 8) | (b[i] & 0xFF); return v; }

    // ---- writer ----
    static void writeTagPayload(DataOutputStream o, int type, Tag t) throws IOException {
        switch (type) {
            case TAG_BYTE: o.writeByte(((ByteT) t).v); break;
            case TAG_SHORT: o.writeShort(((ShortT) t).v); break;
            case TAG_INT: o.writeInt(((IntT) t).v); break;
            case TAG_LONG: o.writeLong(((LongT) t).v); break;
            case TAG_FLOAT: o.writeInt(Float.floatToRawIntBits(((FloatT) t).v)); break;
            case TAG_DOUBLE: o.writeLong(Double.doubleToRawLongBits(((DoubleT) t).v)); break;
            case TAG_BYTE_ARRAY: o.writeInt(((ByteArrayT) t).v.length); o.write(((ByteArrayT) t).v); break;
            case TAG_STRING: { byte[] b = ((StrT) t).v.getBytes(java.nio.charset.StandardCharsets.UTF_8); o.writeShort(b.length); o.write(b); break; }
            case TAG_LIST: { ListT l = (ListT) t; o.writeByte(l.type); o.writeInt(l.v.size()); for (Tag e : l.v) writeTagPayload(o, l.type, e); break; }
            case TAG_COMPOUND: { for (Map.Entry<String, Tag> e : ((CompoundT) t).map.entrySet()) { writeType(o, e.getValue()); o.writeShort(w(e.getKey()).length); o.write(w(e.getKey())); writeTagPayload(o, typeOf(e.getValue()), e.getValue()); } o.writeByte(0); break; }
            case TAG_INT_ARRAY: { int[] a = ((IntArrayT) t).v; o.writeInt(a.length); for (int v : a) o.writeInt(v); break; }
            case TAG_LONG_ARRAY: { long[] a = ((LongArrayT) t).v; o.writeInt(a.length); for (long v : a) o.writeLong(v); break; }
            default: throw new IOException("cannot write type " + type);
        }
    }
    static void writeType(DataOutputStream o, Tag t) throws IOException { if (t instanceof CompoundT) o.writeByte(TAG_COMPOUND); else o.writeByte(typeOf(t)); }
    static int typeOf(Tag t) { if (t instanceof ByteT) return TAG_BYTE; if (t instanceof ShortT) return TAG_SHORT; if (t instanceof IntT) return TAG_INT; if (t instanceof LongT) return TAG_LONG; if (t instanceof FloatT) return TAG_FLOAT; if (t instanceof DoubleT) return TAG_DOUBLE; if (t instanceof ByteArrayT) return TAG_BYTE_ARRAY; if (t instanceof StrT) return TAG_STRING; if (t instanceof ListT) return TAG_LIST; if (t instanceof IntArrayT) return TAG_INT_ARRAY; if (t instanceof LongArrayT) return TAG_LONG_ARRAY; if (t instanceof CompoundT) return TAG_COMPOUND; throw new IllegalStateException(); }
    static byte[] w(String s) { return s.getBytes(java.nio.charset.StandardCharsets.UTF_8); }

    static CompoundT readNbtFile(String path, boolean gzip) throws IOException {
        byte[] raw;
        try (InputStream in = gzip ? new java.util.zip.GZIPInputStream(new FileInputStream(path)) : new BufferedInputStream(new FileInputStream(path))) {
            raw = in.readAllBytes();
        }
        NBCtx c = new NBCtx(raw);
        Tag root = readNamedTag(c);
        if (!(root instanceof CompoundT ct)) throw new IOException("root not compound in " + path);
        return ct;
    }

    static void writeNbtFile(String path, CompoundT root, boolean gzip) throws IOException {
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(
            gzip ? new java.util.zip.GZIPOutputStream(new FileOutputStream(path)) : new FileOutputStream(path)))) {
            o.writeByte(TAG_COMPOUND);
            o.writeShort(0);
            writeTagPayload(o, TAG_COMPOUND, root);
        }
    }

    // ---------------------------------------------------------------- palette / block state

    record BlockState(String id, Map<String, String> props) {
        boolean isModded() { return id.contains(":") && !id.startsWith("minecraft:"); }
        String vanillaId() { return id.startsWith("minecraft:") ? id : "minecraft:" + id; }
    }

    static BlockState fromPaletteEntry(CompoundT entry) {
        StrT name = (StrT) entry.map.get("Name");
        String id = name != null ? name.v : "minecraft:air";
        Map<String, String> props = new LinkedHashMap<>();
        if (entry.map.get("Properties") instanceof CompoundT p) {
            for (Map.Entry<String, Tag> e : p.map.entrySet()) props.put(e.getKey(), scalar(e.getValue()));
        }
        return new BlockState(id, props);
    }

    static String scalar(Tag t) {
        if (t instanceof StrT s) return s.v;
        if (t instanceof IntT i) return String.valueOf(i.v);
        if (t instanceof ByteT b) return String.valueOf(b.v);
        if (t instanceof LongT l) return String.valueOf(l.v);
        return "0";
    }

    // ---------------------------------------------------------------- structure model

    static final class SBlock { int x, y, z, state; Map<String, Object> nbt; }
    static final class StructureModel {
        final List<BlockState> palette = new ArrayList<>();
        final List<int[]> paletteIndex = new ArrayList<>(); // id, props
        final List<SBlock> blocks = new ArrayList<>();
        int sizeX, sizeY, sizeZ;

        int stateIndex(BlockState s) {
            for (int i = 0; i < palette.size(); i++) if (palette.get(i).equals(s)) return i;
            palette.add(s);
            return palette.size() - 1;
        }
        void bounds() {
            int mx = 0, my = 0, mz = 0;
            for (SBlock b : blocks) { mx = Math.max(mx, b.x + 1); my = Math.max(my, b.y + 1); mz = Math.max(mz, b.z + 1); }
            sizeX = mx; sizeY = my; sizeZ = mz;
        }
        void shift(int dx, int dy, int dz) { for (SBlock b : blocks) { b.x += dx; b.y += dy; b.z += dz; } }

        void writeStructures(String path, int dataVersion) throws IOException {
            CompoundT root = new CompoundT();
            root.map.put("DataVersion", new IntT(dataVersion));
            root.map.put("size", new ListT(TAG_INT, List.of(new IntT(sizeX), new IntT(sizeY), new IntT(sizeZ))));

            ListT paletteL = new ListT(TAG_COMPOUND, new ArrayList<>());
            for (BlockState s : palette) {
                CompoundT e = new CompoundT();
                e.map.put("Name", new StrT(s.id));
                if (!s.props.isEmpty()) {
                    CompoundT pp = new CompoundT();
                    for (Map.Entry<String, String> q : s.props.entrySet()) pp.map.put(q.getKey(), new StrT(q.getValue()));
                    e.map.put("Properties", pp);
                }
                paletteL.v.add(e);
            }
            root.map.put("palette", paletteL);

            ListT blocksL = new ListT(TAG_COMPOUND, new ArrayList<>());
            for (SBlock b : blocks) {
                CompoundT e = new CompoundT();
                e.map.put("pos", new ListT(TAG_INT, List.of(new IntT(b.x), new IntT(b.y), new IntT(b.z))));
                e.map.put("state", new IntT(b.state));
                if (b.nbt != null) {
                    CompoundT n = new CompoundT();
                    for (Map.Entry<String, Object> g : b.nbt.entrySet()) n.map.put(g.getKey(), (Tag) g.getValue());
                    e.map.put("nbt", n);
                }
                blocksL.v.add(e);
            }
            root.map.put("blocks", blocksL);
            root.map.put("entities", new ListT(TAG_LIST, new ArrayList<>()));
            writeNbtFile(path, root, false);
        }
    }

    // ---------------------------------------------------------------- mode: info

    static void info(String path) throws IOException {
        boolean gz = sniffGzip(path);
        CompoundT root = readNbtFile(path, gz);
        System.out.println("File: " + path + " (gzip=" + gz + ")");
        System.out.println("Root keys: " + root.map.keySet());
        if (root.map.containsKey("Version"))
            System.out.println("Litematic Version: " + ((IntT) root.map.get("Version")).v);
        Object regions = root.map.get("Regions");
        if (regions instanceof CompoundT rc) {
            for (Map.Entry<String, Tag> e : rc.map.entrySet()) {
                CompoundT reg = (CompoundT) e.getValue();
                int[] sz = vec3(reg.map.get("Size"));
                int[] pos = vec3(reg.map.get("Position"));
                long[] states = ((LongArrayT) reg.map.get("BlockStates")).v;
                int paletteSize = ((ListT) reg.map.get("BlockStatePalette")).v.size();
                System.out.printf("  Region '%s' size=[%d,%d,%d] pos=%s palette=%d stateLongs=%d%n",
                        e.getKey(), sz[0], sz[1], sz[2], Arrays.toString(pos), paletteSize, states.length);
                System.out.println("  Palette:");
                int i = 0;
                for (Tag t : ((ListT) reg.map.get("BlockStatePalette")).v) {
                    BlockState bs = fromPaletteEntry((CompoundT) t);
                    System.out.println("    [" + i++ + "] " + bs.id + (bs.props.isEmpty() ? "" : " " + bs.props));
                    if (i > 200) { System.out.println("    ..."); break; }
                }
                // count
                int bits = bitsForPalette(paletteSize);
                long count = (long) sz[0] * sz[1] * sz[2];
                int[] counts = new int[paletteSize];
                for (long idx = 0; idx < count; idx++) {
                    int longIdx = (int) (idx / (64 / bits));
                    int shift = (int) ((idx % (64 / bits)) * bits);
                    int v = (int) ((states[longIdx] >>> shift) & ((1L << bits) - 1));
                    if (v < paletteSize) counts[v]++;
                }
                System.out.print("  Block counts by palette: ");
                for (int k = 0; k < paletteSize; k++) if (counts[k] > 0) System.out.print("[" + k + "]=" + counts[k] + " ");
                System.out.println();
            }
        }
        if (root.map.containsKey("DataVersion"))
            System.out.println("DataVersion: " + ((IntT) root.map.get("DataVersion")).v);
    }

    static boolean sniffGzip(String path) throws IOException {
        try (InputStream in = new FileInputStream(path)) { byte[] b = in.readNBytes(2); return (b[0] & 0xFF) == 0x1F && (b[1] & 0xFF) == 0x8B; }
    }
    static int[] intArr(Tag t) {
        if (t instanceof IntArrayT a) return a.v;
        if (t instanceof LongArrayT la) { int[] r = new int[la.v.length]; for (int i = 0; i < r.length; i++) r[i] = (int) la.v[i]; return r; }
        if (t instanceof ListT l) {
            int[] r = new int[l.v.size()];
            for (int i = 0; i < r.length; i++) r[i] = num(l.v.get(i));
            return r;
        }
        if (t instanceof ByteT b) return new int[]{ b.v };
        if (t instanceof LongT l) return new int[]{ (int) l.v };
        if (t instanceof IntT i) return new int[]{ i.v };
        throw new IllegalStateException("not int array: " + (t == null ? "null" : t.getClass().getSimpleName()));
    }
    static int[] vec3(Tag t) {
        if (t instanceof CompoundT c) return new int[]{ num(c.map.get("x")), num(c.map.get("y")), num(c.map.get("z")) };
        return intArr(t);
    }
    static int num(Tag t) {
        if (t instanceof IntT i) return i.v;
        if (t instanceof LongT l) return (int) l.v;
        if (t instanceof ByteT b) return b.v;
        if (t instanceof ShortT s) return s.v;
        if (t instanceof CompoundT c && c.map.containsKey("value")) return num(c.map.get("value"));
        if (t instanceof StrT s) return (int) Double.parseDouble(s.v);
        throw new IllegalStateException("not numeric: " + (t == null ? "null" : t.getClass().getSimpleName()));
    }
    static int bitsForPalette(int size) { return Math.max(1, 63 - Long.numberOfLeadingZeros(size - 1L)); }

    // ---------------------------------------------------------------- mode: litematic -> structure

    static void litematic(String inPath, String outPath, String regionName) throws IOException {
        CompoundT root = readNbtFile(inPath, true);
        CompoundT regions = (CompoundT) root.map.get("Regions");
        if (regionName == null) {
            if (regions.map.size() == 1) regionName = regions.map.keySet().iterator().next();
            else throw new IOException("Multiple regions; specify one of " + regions.map.keySet());
        }
        CompoundT reg = (CompoundT) regions.map.get(regionName);
        int[] sz = vec3(reg.map.get("Size"));
        for (int i = 0; i < sz.length; i++) sz[i] = Math.abs(sz[i]);
        ListT paletteL = (ListT) reg.map.get("BlockStatePalette");
        long[] states = reg.map.containsKey("BlockStates") ? ((LongArrayT) reg.map.get("BlockStates")).v : new long[0];
        int bits = bitsForPalette(paletteL.v.size());

        StructureModel sm = new StructureModel();
        Map<String, Tag> blockEntities = new LinkedHashMap<>();
        Tag belTag = reg.map.containsKey("TileEntities") ? reg.map.get("TileEntities")
                : reg.map.containsKey("BlockEntities") ? reg.map.get("BlockEntities") : null;
        if (belTag instanceof ListT bel) {
            for (Tag t : bel.v) {
                CompoundT c = (CompoundT) t;
                int[] p = vec3(c);
                String key = p[0] + "," + p[1] + "," + p[2];
                CompoundT nbt = new CompoundT();
                for (Map.Entry<String, Tag> e : c.map.entrySet()) {
                    if (e.getKey().equals("x") || e.getKey().equals("y") || e.getKey().equals("z") || e.getKey().equals("pos")) continue;
                    nbt.map.put(e.getKey(), e.getValue());
                }
                blockEntities.put(key, nbt);
            }
        }

        long count = (long) sz[0] * sz[1] * sz[2];
        int elementsPerLong = 64 / bits;
        long mask = (1L << bits) - 1;
        int unknown = 0;
        for (long idx = 0; idx < count; idx++) {
            int longIdx = (int) (idx / elementsPerLong);
            int shift = (int) ((idx % elementsPerLong) * bits);
            int v = (int) ((states[longIdx] >>> shift) & mask);
            if (v >= paletteL.v.size()) continue;
            if (paletteL.v.size() == 0) continue;
            BlockState bs = fromPaletteEntry((CompoundT) paletteL.v.get(v));
            if (bs.id.equals("minecraft:air") || bs.id.equals("air") || bs.id.equals("structure_void")) continue;
            if (bs.isModded()) { unknown++; continue; }
            int x = (int) (idx % sz[0]);
            int z = (int) ((idx / sz[0]) % sz[2]);
            int y = (int) (idx / (sz[0] * sz[2]));
            int si = sm.stateIndex(new BlockState(bs.vanillaId(), bs.props));
            SBlock b = new SBlock();
            b.x = x; b.y = y; b.z = z; b.state = si;
            sm.blocks.add(b);
        }
        // attach block entity nbt only where the block exists and the entity id matches the block
        Set<String> paletteIds = new HashSet<>();
        for (Tag t : paletteL.v) paletteIds.add(fromPaletteEntry((CompoundT) t).id);

        for (SBlock b : sm.blocks) {
            String key = b.x + "," + b.y + "," + b.z;
            if (!blockEntities.containsKey(key)) continue;
            CompoundT ent = (CompoundT) blockEntities.get(key);
            StrT entId = (StrT) ent.map.get("id");
            String entBlock = entId != null ? entId.v : "";
            if (paletteIds.contains(entBlock)) b.nbt = (Map) ent.map;
            else System.err.println("Skipping block entity " + entBlock + " at " + key + " (not in 1.21.4 palette)");
        }
        if (unknown > 0) System.err.println("Skipped " + unknown + " non-vanilla blocks");
        sm.bounds();
        sm.writeStructures(outPath, 4189);
        System.out.println("Wrote " + outPath + " size=" + sm.sizeX + "x" + sm.sizeY + "x" + sm.sizeZ +
                " blocks=" + sm.blocks.size() + " palette=" + sm.palette.size());
    }

    // ---------------------------------------------------------------- mode: probe (litematic -> console slices, no write)

    static void probeLitematic(String inPath) throws IOException {
        CompoundT root = readNbtFile(inPath, sniffGzip(inPath));
        CompoundT regions = (CompoundT) root.map.get("Regions");
        for (Map.Entry<String, Tag> e : regions.map.entrySet()) {
            CompoundT reg = (CompoundT) e.getValue();
            int[] sz = vec3(reg.map.get("Size"));
            for (int i = 0; i < sz.length; i++) sz[i] = Math.abs(sz[i]);
            ListT paletteL = (ListT) reg.map.get("BlockStatePalette");
            long[] states = reg.map.containsKey("BlockStates") ? ((LongArrayT) reg.map.get("BlockStates")).v : new long[0];
            int bits = bitsForPalette(paletteL.v.size());
            char[] light = new char[paletteL.v.size()];
            for (int i = 0; i < paletteL.v.size(); i++) {
                BlockState bs = fromPaletteEntry((CompoundT) paletteL.v.get(i));
                light[i] = '_';
                if (bs.id.equals("minecraft:air") || bs.id.equals("air") || bs.id.equals("structure_void")) light[i] = '.';
            }
            long count = (long) sz[0] * sz[1] * sz[2];
            int epl = 64 / bits;
            long mask = (1L << bits) - 1;
            for (int y : new int[]{ 5, 8, 11, 14 }) {
                char[][] grid = new char[sz[2]][sz[0]];
                for (int z = 0; z < sz[2]; z++) Arrays.fill(grid[z], '.');
                for (long idx = 0; idx < count; idx++) {
                    int v = (int) ((states[(int) (idx / epl)] >>> ((int) ((idx % epl) * bits))) & mask);
                    if (v >= paletteL.v.size()) continue;
                    if (light[v] == '.') continue;
                    int x = (int) (idx % sz[0]);
                    int z = (int) ((idx / sz[0]) % sz[2]);
                    int yy = (int) (idx / (sz[0] * sz[2]));
                    if (yy != y) continue;
                    grid[z][x] = light[v];
                }
                System.out.println("region '" + e.getKey() + "' size=" + Arrays.toString(sz) + " slice y=" + y);
                for (int z = 0; z < sz[2]; z++) System.out.println(new String(grid[z]));
            }
        }
    }

    // ---------------------------------------------------------------- mode: hollow (carve bulk interior, keep shell + guaranteed floor plates)

    static boolean isBulkFill(String id) {
        return id.endsWith("_wool") || id.equals("minecraft:smooth_stone") || id.equals("minecraft:stone")
            || id.equals("minecraft:smooth_basalt") || id.equals("minecraft:deepslate") || id.equals("minecraft:cobblestone");
    }

    static void hollow(String inPath, String outPath, int floorEvery) throws IOException {
        CompoundT root = readNbtFile(inPath, sniffGzip(inPath));
        int[] sz = intArr(root.map.get("size"));
        ListT pl = (ListT) root.map.get("palette");
        List<BlockState> pal = new ArrayList<>();
        for (Tag t : pl.v) pal.add(fromPaletteEntry((CompoundT) t));
        StructureModel acc = new StructureModel();
        Set<String> occ = new HashSet<>();
        String floorBlock = "minecraft:smooth_stone";
        for (Tag t : ((ListT) root.map.get("blocks")).v) {
            CompoundT c = (CompoundT) t;
            int state = ((IntT) c.map.get("state")).v;
            BlockState bs = pal.get(state);
            int[] pos = intArr(c.map.get("pos"));
            int x = pos[0], y = pos[1], z = pos[2];
            boolean edge = x == 0 || x == sz[0] - 1 || z == 0 || z == sz[2] - 1;
            boolean floorLevel = floorEvery > 0 && y % floorEvery == 0;
            boolean bulk = isBulkFill(bs.id);
            SBlock sb = new SBlock();
            sb.x = x; sb.y = y; sb.z = z;
            if (!bulk || edge || floorLevel) { // keep shell, floor plates and all decorations
                sb.state = acc.stateIndex(bs);
                sb.nbt = c.map.get("nbt") instanceof CompoundT n ? (Map) n.map : null;
                acc.blocks.add(sb);
                occ.add(x + "," + y + "," + z);
            }
        }
        int fill = acc.stateIndex(new BlockState(floorBlock, Map.of()));
        for (int y = 0; y < sz[1] && floorEvery > 0; y += floorEvery) {
            for (int x = 1; x < sz[0] - 1; x++) for (int z = 1; z < sz[2] - 1; z++) {
                if (!occ.contains(x + "," + y + "," + z)) {
                    SBlock sb = new SBlock();
                    sb.x = x; sb.y = y; sb.z = z; sb.state = fill;
                    acc.blocks.add(sb);
                    occ.add(x + "," + y + "," + z);
                }
            }
        }
        acc.bounds();
        acc.writeStructures(outPath, 4189);
        System.out.println("Hollow " + outPath + " size=" + acc.sizeX + "x" + acc.sizeY + "x" + acc.sizeZ +
                " blocks=" + acc.blocks.size() + " palette=" + acc.palette.size());
    }

    // ---------------------------------------------------------------- mode: floors (add walkable plates to sparse shells, keep everything else)

    static void floors(String inPath, String outPath, int floorEvery, String fillBlock) throws IOException {
        CompoundT root = readNbtFile(inPath, sniffGzip(inPath));
        int[] sz = intArr(root.map.get("size"));
        ListT pl = (ListT) root.map.get("palette");
        List<BlockState> pal = new ArrayList<>();
        for (Tag t : pl.v) pal.add(fromPaletteEntry((CompoundT) t));
        StructureModel acc = new StructureModel();
        Set<String> occ = new HashSet<>();
        for (Tag t : ((ListT) root.map.get("blocks")).v) {
            CompoundT c = (CompoundT) t;
            int state = ((IntT) c.map.get("state")).v;
            BlockState bs = pal.get(state);
            int[] pos = intArr(c.map.get("pos"));
            SBlock sb = new SBlock();
            sb.x = pos[0]; sb.y = pos[1]; sb.z = pos[2];
            sb.state = acc.stateIndex(bs);
            sb.nbt = c.map.get("nbt") instanceof CompoundT n ? (Map) n.map : null;
            acc.blocks.add(sb);
            occ.add(sb.x + "," + sb.y + "," + sb.z);
        }
        int fill = acc.stateIndex(new BlockState(fillBlock, Map.of()));
        for (int y = 0; y < sz[1] && floorEvery > 0; y += floorEvery) {
            for (int x = 1; x < sz[0] - 1; x++) for (int z = 1; z < sz[2] - 1; z++) {
                if (!occ.contains(x + "," + y + "," + z)) {
                    SBlock sb = new SBlock();
                    sb.x = x; sb.y = y; sb.z = z; sb.state = fill;
                    acc.blocks.add(sb);
                    occ.add(x + "," + y + "," + z);
                }
            }
        }
        acc.bounds();
        acc.writeStructures(outPath, 4189);
        System.out.println("Floors " + outPath + " size=" + acc.sizeX + "x" + acc.sizeY + "x" + acc.sizeZ +
                " blocks=" + acc.blocks.size() + " palette=" + acc.palette.size());
    }

    // ---------------------------------------------------------------- mode: schematic (Sponge .schem v1-v3 -> structure)

    static final Set<String> SNAPSHOT_BLOCKS = Set.of(
            "minecraft:bush", "minecraft:cactus_flower",
            "minecraft:copper_chest", "minecraft:waxed_copper_chest",
            "minecraft:copper_golem_statue", "minecraft:waxed_copper_golem_statue",
            "minecraft:oak_shelf", "minecraft:spruce_shelf", "minecraft:birch_shelf", "minecraft:jungle_shelf",
            "minecraft:acacia_shelf", "minecraft:dark_oak_shelf", "minecraft:mangrove_shelf", "minecraft:cherry_shelf",
            "minecraft:bamboo_shelf", "minecraft:crimson_shelf", "minecraft:warped_shelf", "minecraft:pale_oak_shelf");

    static BlockState parseSchemaState(String st) {
        int br = st.indexOf('[');
        String id = br == -1 ? st : st.substring(0, br);
        Map<String, String> props = new LinkedHashMap<>();
        if (br != -1) {
            int end = st.lastIndexOf(']');
            String inside = end < 0 ? "" : st.substring(br + 1, end);
            if (!inside.isEmpty()) for (String kv : inside.split(",")) {
                int eq = kv.indexOf('=');
                if (eq > 0) props.put(kv.substring(0, eq), kv.substring(eq + 1));
            }
        }
        return new BlockState(id, props);
    }

    static int readVarint(byte[] b, int[] c) throws IOException {
        int val = 0, shift = 0, guard = 0;
        while (true) {
            if (c[0] >= b.length) throw new EOFException("varint overrun");
            int x = b[c[0]++] & 0xFF;
            val |= (x & 0x7F) << shift;
            if ((x & 0x80) == 0) break;
            shift += 7;
            if (++guard > 5) throw new IOException("varint too long");
        }
        return val;
    }

    static int[] decodeFlat(byte[] data, long volume, boolean varint) {
        int[] out = new int[(int) volume];
        if (varint) {
            int[] cp = { 0 };
            try {
                for (int i = 0; i < volume; i++) out[i] = readVarint(data, cp);
                if (cp[0] != data.length) throw new IOException("varint consumed " + cp[0] + " of " + data.length);
            } catch (IOException ex) {
                System.err.println("varint decode failed (" + ex.getMessage() + "); falling back to plain bytes");
                cp[0] = 0;
                for (int i = 0; i < volume && i < data.length; i++) out[i] = data[i] & 0xFF;
            }
        } else {
            for (int i = 0; i < volume && i < data.length; i++) out[i] = data[i] & 0xFF;
        }
        return out;
    }

    static void schematic(String inPath, String outPath) throws IOException {
        CompoundT root = readNbtFile(inPath, sniffGzip(inPath));
        CompoundT s;
        if (root.map.get("Schematic") instanceof CompoundT sc) s = sc; else s = root;
        int ver = s.map.get("Version") instanceof IntT iv ? iv.v : 1;
        int w = num(s.map.getOrDefault("Width", new IntT(0)));
        int h = num(s.map.getOrDefault("Height", new IntT(0)));
        int l = num(s.map.getOrDefault("Length", new IntT(0)));
        if (w <= 0 || h <= 0 || l <= 0) throw new IOException(inPath + ": bad dimensions " + w + "x" + h + "x" + l);

        CompoundT paletteMap;
        byte[] data;
        List<Tag> bents = List.of();
        if (s.map.get("Blocks") instanceof CompoundT blk) {
            paletteMap = (CompoundT) blk.map.get("Palette");
            data = blk.map.get("Data") instanceof ByteArrayT ba ? ba.v : new byte[0];
            if (blk.map.get("BlockEntities") instanceof ListT be) bents = be.v;
            if (ver < 3) ver = 3;
        } else {
            paletteMap = (CompoundT) s.map.get("Palette");
            data = s.map.get("BlockData") instanceof ByteArrayT ba ? ba.v : new byte[0];
            if (s.map.get("BlockEntities") instanceof ListT be) bents = be.v;
            else if (s.map.get("Tiles") instanceof ListT be) bents = be.v;
        }

        List<String> palStr = new ArrayList<>();
        for (Map.Entry<String, Tag> e : paletteMap.map.entrySet())
            if (e.getValue() instanceof IntT idx) {
                while (palStr.size() <= idx.v) palStr.add(null);
                palStr.set(idx.v, e.getKey());
            }

        long volume = (long) w * h * l;
        int[] decoded = decodeFlat(data, volume, !(s.map.get("Blocks") instanceof CompoundT) || ver >= 3);

        Map<String, Tag> beMap = new LinkedHashMap<>();
        for (Tag t : bents) {
            CompoundT c = (CompoundT) t;
            int[] p = c.map.get("Pos") instanceof Tag tt ? vec3(tt)
                    : new int[]{ num(c.map.getOrDefault("x", new IntT(0))), num(c.map.getOrDefault("y", new IntT(0))), num(c.map.getOrDefault("z", new IntT(0))) };
            CompoundT nbt = new CompoundT();
            if (c.map.get("Id") instanceof StrT id) nbt.map.put("id", new StrT(id.v));
            for (Map.Entry<String, Tag> e : c.map.entrySet()) {
                String k = e.getKey();
                if (k.equals("Id") || k.equals("Pos") || k.equals("pos") || k.equals("x") || k.equals("y") || k.equals("z")) continue;
                if (k.equals("Data") && e.getValue() instanceof CompoundT dc) {
                    for (Map.Entry<String, Tag> g : dc.map.entrySet()) nbt.map.put(g.getKey(), g.getValue());
                    continue;
                }
                if (nbt.map.containsKey(k)) continue;
                nbt.map.put(k, e.getValue());
            }
            beMap.put(p[0] + "," + p[1] + "," + p[2], nbt);
        }

        StructureModel acc = new StructureModel();
        int skipMod = 0, skipSnap = 0;
        for (int i = 0; i < volume; i++) {
            int v = decoded[i];
            if (v < 0 || v >= palStr.size() || palStr.get(v) == null) continue;
            String st = palStr.get(v);
            BlockState bs = parseSchemaState(st);
            String id = bs.id;
            if (id.equals("minecraft:air") || id.equals("air") || id.equals("minecraft:structure_void") || id.equals("structure_void")) continue;
            if (bs.isModded()) { skipMod++; continue; }
            if (SNAPSHOT_BLOCKS.contains(id)) { skipSnap++; continue; }
            int x = i % w;
            int z = (i / w) % l;
            int y = i / (w * l);
            SBlock sb = new SBlock();
            sb.x = x; sb.y = y; sb.z = z;
            sb.state = acc.stateIndex(bs);
            acc.blocks.add(sb);
        }
        if (skipMod > 0) System.err.println("Skipped " + skipMod + " modded blocks");
        if (skipSnap > 0) System.err.println("Skipped " + skipSnap + " snapshot-only block ids");

        for (SBlock b : acc.blocks) {
            Tag e = beMap.get(b.x + "," + b.y + "," + b.z);
            if (e instanceof CompoundT cn) b.nbt = (Map) cn.map;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (SBlock b : acc.blocks) { minX = Math.min(minX, b.x); minY = Math.min(minY, b.y); minZ = Math.min(minZ, b.z); }
        if (acc.blocks.isEmpty()) throw new IOException(inPath + ": no blocks converted");
        acc.shift(-minX, -minY, -minZ);
        acc.bounds();
        acc.writeStructures(outPath, 4189);
        System.out.println("Schem " + outPath + " size=" + acc.sizeX + "x" + acc.sizeY + "x" + acc.sizeZ +
                " blocks=" + acc.blocks.size() + " palette=" + acc.palette.size());
    }

    // ---------------------------------------------------------------- mode: norm (re-normalize a structure nbt, drop jigsaw/void)

    static void norm(String inPath, String outPath) throws IOException {
        CompoundT root = readNbtFile(inPath, sniffGzip(inPath));
        ListT pl = (ListT) root.map.get("palette");
        List<BlockState> pal = new ArrayList<>();
        for (Tag t : pl.v) pal.add(fromPaletteEntry((CompoundT) t));
        StructureModel acc = new StructureModel();
        ListT bl = (ListT) root.map.get("blocks");
        for (Tag t : bl.v) {
            CompoundT c = (CompoundT) t;
            BlockState bs = pal.get(((IntT) c.map.get("state")).v);
            if (bs.id.equals("minecraft:structure_void") || bs.id.equals("minecraft:jigsaw") || bs.id.equals("minecraft:structure_block")) continue;
            int[] pos = intArr(c.map.get("pos"));
            SBlock sb = new SBlock();
            sb.x = pos[0]; sb.y = pos[1]; sb.z = pos[2];
            sb.state = acc.stateIndex(bs);
            if (c.map.get("nbt") instanceof CompoundT n) sb.nbt = (Map) n.map;
            acc.blocks.add(sb);
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (SBlock b : acc.blocks) { minX = Math.min(minX, b.x); minY = Math.min(minY, b.y); minZ = Math.min(minZ, b.z); }
        acc.shift(-minX, -minY, -minZ);
        acc.bounds();
        acc.writeStructures(outPath, 4189);
        System.out.println("Norm " + outPath + " size=" + acc.sizeX + "x" + acc.sizeY + "x" + acc.sizeZ +
                " blocks=" + acc.blocks.size() + " palette=" + acc.palette.size());
    }

    // ---------------------------------------------------------------- mode: pack (jigsaw stack)

    record Jig(int x, int y, int z, boolean up, String name, String target, String finalState) {}

    static void pack(String outPath, List<String> pieces, String fallbackFill) throws IOException {
        record Piece(String path, int[] size, List<BlockState> pal, List<CompoundT> blocks, List<Jig> jigs) {}
        List<Piece> built = new ArrayList<>();
        for (String p : pieces) {
            CompoundT root = readNbtFile(p, sniffGzip(p));
            int[] sz = intArr(root.map.get("size"));
            ListT pl = (ListT) root.map.get("palette");
            List<BlockState> pal = new ArrayList<>();
            for (Tag t : pl.v) pal.add(fromPaletteEntry((CompoundT) t));
            ListT bl = (ListT) root.map.get("blocks");
            List<CompoundT> blocks = new ArrayList<>();
            for (Tag t : bl.v) blocks.add((CompoundT) t);
            List<Jig> jigs = new ArrayList<>();
            for (int i = 0; i < pal.size(); i++) if (pal.get(i).id.equals("minecraft:jigsaw")) {
                int k = i;
                for (CompoundT b : blocks) {
                    int st = ((IntT) b.map.get("state")).v;
                    if (st != k) continue;
                    int[] pos = intArr(b.map.get("pos"));
                    CompoundT n = b.map.get("nbt") instanceof CompoundT c ? c : null;
                    String orient = pal.get(k).props.get("orientation");
                    boolean up = orient != null && orient.startsWith("up_");
                    String name = n == null || !(n.map.get("name") instanceof StrT s) ? "" : s.v;
                    String target = n == null || !(n.map.get("target") instanceof StrT t2) ? "" : t2.v;
                    String fs = n == null || !(n.map.get("final_state") instanceof StrT f) ? fallbackFill : f.v;
                    jigs.add(new Jig(pos[0], pos[1], pos[2], up, name, target, fs));
                }
            }
            built.add(new Piece(p, sz, pal, blocks, jigs));
        }

        StructureModel acc = new StructureModel();
        List<int[]> fillCells = new ArrayList<>(); // jigsaw cells to fill with block
        Map<String, String> fillBlock = new HashMap<>();
        int[] cursor = null; // absolute position of the connection anchor cell of previous piece
        for (int idx = 0; idx < built.size(); idx++) {
            Piece pc = built.get(idx);
            boolean first = idx == 0;
            int[] off = new int[3];

            if (first) {
                // normalize this piece so its lowest solid y becomes build-ground 0, x/z kept as-is then shifted later
                int minY = Integer.MAX_VALUE;
                for (CompoundT b : pc.blocks) {
                    int si = ((IntT) b.map.get("state")).v;
                    BlockState bs = pc.pal.get(si);
                    if (bs.id.equals("minecraft:jigsaw") || bs.id.equals("minecraft:structure_void")) continue;
                    minY = Math.min(minY, intArr(b.map.get("pos"))[1]);
                }
                if (minY != Integer.MAX_VALUE) off[1] = -minY;
                // find connecting (up) jigsaw
                for (Jig j : pc.jigs) if (j.up) { cursor = new int[]{ j.x, j.y + off[1], j.z }; fillCell(fillCells, fillBlock, cursor, j.finalState); }
            } else {
                // choose this piece's bottom jigsaw that matches previous top
                Jig chosen = null;
                for (Jig j : pc.jigs) if (!j.up && j.name.equals(connectorName)) chosen = j;
                if (chosen == null) throw new IOException(pc.path + ": no bottom jigsaw for target " + connectorName);
                off = new int[]{ cursor[0] - chosen.x, cursor[1] - chosen.y, cursor[2] - chosen.z };
                fillCell(fillCells, fillBlock, cursor, chosen.finalState);
                cursor = null;
                for (Jig j : pc.jigs) if (j.up) { cursor = new int[]{ j.x + off[0], j.y + off[1], j.z + off[2] }; fillCell(fillCells, fillBlock, cursor, j.finalState); }
            }
            // set expected connector name for the NEXT piece = this piece's up jigsaw target
            connectorName = null;
            for (Jig j : pc.jigs) if (j.up) connectorName = j.target;

            for (CompoundT b : pc.blocks) {
                int si = ((IntT) b.map.get("state")).v;
                BlockState bs = pc.pal.get(si);
                if (bs.id.equals("minecraft:jigsaw") || bs.id.equals("minecraft:structure_void") || bs.id.equals("minecraft:structure_block")) continue;
                int[] pos = intArr(b.map.get("pos"));
                SBlock sb = new SBlock();
                sb.x = pos[0] + off[0]; sb.y = pos[1] + off[1]; sb.z = pos[2] + off[2];
                sb.state = acc.stateIndex(bs);
                if (b.map.get("nbt") instanceof CompoundT n) sb.nbt = (Map) n.map;
                putBlock(acc, sb);
            }
        }
        for (int[] cell : fillCells) {
            SBlock sb = new SBlock();
            sb.x = cell[0]; sb.y = cell[1]; sb.z = cell[2];
            sb.state = acc.stateIndex(new BlockState(fillBlock.get(cell[0] + "," + cell[1] + "," + cell[2]), Map.of()));
            putBlock(acc, sb);
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (SBlock b : acc.blocks) { minX = Math.min(minX, b.x); minY = Math.min(minY, b.y); minZ = Math.min(minZ, b.z); }
        acc.shift(-minX, -minY, -minZ);
        acc.bounds();
        // merge identical palette entries already handled by stateIndex; but fills may duplicate cells -> let later dedupe
        acc.writeStructures(outPath, 4189);
        System.out.println("Wrote " + outPath + " size=" + acc.sizeX + "x" + acc.sizeY + "x" + acc.sizeZ +
                " blocks=" + acc.blocks.size() + " palette=" + acc.palette.size());
    }

    static String connectorName;

    static void putBlock(StructureModel m, SBlock b) {
        for (SBlock e : m.blocks) if (e.x == b.x && e.y == b.y && e.z == b.z) { e.state = b.state; e.nbt = b.nbt; return; }
        m.blocks.add(b);
    }

    static void fillCell(List<int[]> cells, Map<String, String> blocks, int[] cell, String block) {
        cells.add(cell);
        blocks.put(cell[0] + "," + cell[1] + "," + cell[2], block.startsWith("minecraft:") ? block : "minecraft:" + block);
    }

    // ---------------------------------------------------------------- main

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage:");
            System.out.println("  info <file>");
            System.out.println("  tree <file>");
            System.out.println("  litem <in.litematic> <out.nbt> [region]");
            System.out.println("  schem <in.schem> <out.nbt>");
            System.out.println("  norm <in.nbt> <out.nbt>");
            System.out.println("  pack <out.nbt> <piece1.nbt> [piece2 ...]");
            System.exit(1);
        }
        switch (args[0]) {
            case "info" -> info(args[1]);
            case "tree" -> tree(args[1]);
            case "jig" -> jig(args[1]);
            case "slice" -> slice(args[1], Integer.parseInt(args[2]));
            case "probe" -> probeLitematic(args[1]);
            case "levels" -> levels(args[1]);
            case "hollow" -> hollow(args[1], args[2], Integer.parseInt(args[3]));
            case "floors" -> floors(args[1], args[2], Integer.parseInt(args[3]), args[4]);
            case "dbg" -> dbg(args[1]);
            case "btower" -> btower(args[1]);
            case "sfloor" -> sfloor(args[1], args[2]);
            case "litem" -> litematic(args[1], args[2], args.length > 3 ? args[3] : null);
            case "schem" -> schematic(args[1], args[2]);
            case "norm" -> norm(args[1], args[2]);
            case "pack" -> pack(args[1], Arrays.asList(Arrays.copyOfRange(args, 2, args.length)), "minecraft:chiseled_deepslate");
            case "gen" -> generateAll(args[1]);
            default -> { System.err.println("unknown mode " + args[0]); System.exit(1); }
        }
    }

    // ---------------------------------------------------------------- mode: gen (procedural buildings)

    static final class B {
        final StructureModel m = new StructureModel();
        void set(int x, int y, int z, String id, Map<String, String> props) {
            SBlock b = new SBlock();
            b.x = x; b.y = y; b.z = z;
            b.state = m.stateIndex(new BlockState(id, props));
            m.blocks.add(b);
        }
        void set(int x, int y, int z, String id) { set(x, y, z, id, Map.of()); }
        void box(int x0, int y0, int z0, int x1, int y1, int z1, String id) {
            for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) set(x, y, z, id);
        }
        void shell(int x0, int y0, int z0, int x1, int y1, int z1, String id) {
            for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++)
                if (x == x0 || x == x1 || y == y0 || y == y1 || z == z0 || z == z1) set(x, y, z, id);
        }
        void frame(int x0, int y0, int z0, int x1, int y1, int z1, String id) { // vertical corner pillars + top/bottom edge
            for (int x : new int[]{ x0, x1 }) for (int y = y0; y <= y1; y++) for (int z : new int[]{ z0, z1 }) set(x, y, z, id);
        }
        void glassWall(int x0, int y0, int z0, int x1, int y1, int z1) {
            for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++)
                if ((x - x0) % 4 != 0 || y % 3 == 2) set(x, y, z, "minecraft:glass");
        }
        void write(String out) throws IOException {
            m.bounds();
            m.writeStructures(out, 4189);
            System.out.println("Gen " + out + " size=" + m.sizeX + "x" + m.sizeY + "x" + m.sizeZ +
                    " blocks=" + m.blocks.size() + " palette=" + m.palette.size());
        }
    }

    // --- 1) modern villa: white concrete mega-box, glass front, flat roof, pool + garden
    static void genVilla(B b) {
        int G = 0; // ground level y=0 slab base
        b.box(0, 0, 0, 17, 0, 17, "minecraft:white_concrete"); // patio slab
        // main block 4..15 x 4..15
        b.box(4, 0, 4, 12, 0, 12, "minecraft:smooth_quartz"); // internal floor
        // walls y1..4
        b.frame(4, 1, 4, 12, 4, 12, "minecraft:white_concrete");
        // front (z=12) full glass y2..3 between pillars, glass panes
        for (int x = 5; x <= 11; x++) { b.set(x, 2, 12, "minecraft:glass"); b.set(x, 3, 12, "minecraft:glass"); }
        for (int y = 1; y <= 4; y++) { b.set(4, y, 4, "minecraft:smooth_quartz"); b.set(12, y, 4, "minecraft:smooth_quartz"); }
        for (int y = 1; y <= 4; y++) { b.set(4, y, 12, "minecraft:smooth_quartz"); b.set(12, y, 12, "minecraft:smooth_quartz"); }
        // side glass (x=12, z5..11)
        for (int z = 5; z <= 11; z++) { b.set(12, 2, z, "minecraft:glass"); b.set(12, 3, z, "minecraft:glass"); }
        // roof y5 flat with overhang trim
        b.box(2, 5, 2, 14, 5, 14, "minecraft:white_concrete");
        for (int x = 2; x <= 14; x++) { b.set(x, 6, 2, "minecraft:smooth_quartz"); b.set(x, 6, 14, "minecraft:smooth_quartz"); }
        for (int z = 2; z <= 14; z++) { b.set(2, 6, z, "minecraft:smooth_quartz"); b.set(14, 6, z, "minecraft:smooth_quartz"); }
        // interior furniture accents: quartz sofa, dark oak table, lanterns
        b.set(6, 1, 6, "minecraft:dark_oak_planks"); b.set(7, 1, 6, "minecraft:dark_oak_planks"); b.set(6, 1, 7, "minecraft:dark_oak_planks"); b.set(7, 1, 7, "minecraft:dark_oak_planks");
        b.set(6, 2, 6, "minecraft:sea_lantern");
        b.set(8, 1, 7, "minecraft:white_wool"); b.set(9, 1, 7, "minecraft:white_wool");
        b.set(8, 2, 7, "minecraft:sea_lantern");
        // door
        b.set(5, 1, 12, "minecraft:dark_oak_door", Map.of("facing", "south", "half", "lower", "hinge", "left", "open", "false", "powered", "false"));
        b.set(5, 2, 12, "minecraft:dark_oak_door", Map.of("facing", "south", "half", "upper", "hinge", "left", "open", "false", "powered", "false"));
        // pool at 0..3 x 0..17 strip? place pool z 0..3
        b.box(0, 0, 0, 3, 0, 5, "minecraft:smooth_quartz");
        b.set(0, 1, 0, "minecraft:light_blue_concrete"); b.set(1, 1, 0, "minecraft:light_blue_concrete"); b.set(2, 1, 0, "minecraft:light_blue_concrete"); b.set(3, 1, 0, "minecraft:light_blue_concrete");
        b.set(0, 1, 5, "minecraft:light_blue_concrete"); b.set(1, 1, 5, "minecraft:light_blue_concrete"); b.set(2, 1, 5, "minecraft:light_blue_concrete"); b.set(3, 1, 5, "minecraft:light_blue_concrete");
        b.set(0, 1, 1, "minecraft:water"); b.set(1, 1, 1, "minecraft:water"); b.set(2, 1, 1, "minecraft:water"); b.set(3, 1, 1, "minecraft:water");
        b.set(0, 1, 2, "minecraft:water"); b.set(1, 1, 2, "minecraft:water"); b.set(2, 1, 2, "minecraft:water"); b.set(3, 1, 2, "minecraft:water");
        b.set(0, 1, 3, "minecraft:water"); b.set(1, 1, 3, "minecraft:water"); b.set(2, 1, 3, "minecraft:water"); b.set(3, 1, 3, "minecraft:water");
        b.set(0, 1, 4, "minecraft:water"); b.set(1, 1, 4, "minecraft:water"); b.set(2, 1, 4, "minecraft:water"); b.set(3, 1, 4, "minecraft:water");
        // canopy
        for (int y = 1; y <= 4; y++) for (int z = 13; z <= 15; z++) { if (z == 15) b.set(8, y, z, "minecraft:dark_oak_fence"); else b.set(8, y, z, "minecraft:dark_oak_log", Map.of("axis", "y")); }
        b.box(6, 5, 13, 10, 5, 16, "minecraft:white_concrete");
        for (int x = 6; x <= 10; x++) { b.set(x, 5, 13, "minecraft:smooth_quartz_slab", Map.of("type", "top")); b.set(x, 5, 16, "minecraft:smooth_quartz_slab", Map.of("type", "top")); }
        b.box(8, 6, 13, 8, 6, 16, "minecraft:sea_lantern");
        // garden: grass + azalea bushes on east (13..17)
        b.box(13, 0, 0, 17, 0, 17, "minecraft:grass_block");
        b.set(14, 1, 3, "minecraft:azalea"); b.set(15, 1, 7, "minecraft:azalea"); b.set(16, 1, 9, "minecraft:azalea");
        b.set(15, 2, 9, "minecraft:flowering_azalea_leaves", Map.of("distance", "1", "persistent", "true", "waterlogged", "false"));
        b.set(14, 1, 12, "minecraft:cherry_sapling");
    }

    // --- 2) modern apartment block: 4 storeys, balconies, glass stair core
    static void genApartment(B b) {
        int W = 13, D = 9, H = 16;
        b.box(0, 0, 0, W, 0, D, "minecraft:stone"); // foundation slab (buried later)
        b.box(0, 1, 0, W, 1, D, "minecraft:white_concrete"); // ground floor slab
        // stair core at x0..2
        b.frame(0, 2, 0, 2, H - 1, 2, "minecraft:gray_concrete");
        b.box(0, H, 0, W, H, D, "minecraft:white_concrete"); // roof
        // floors: each 3 tall (2..16). floors at y=2,5,8,11,14 ceilings support
        int[] floors = { 2, 5, 8, 11, 14 };
        for (int i = 0; i < floors.length; i++) {
            int fy = floors[i];
            for (int x = 3; x <= W; x++) setFloorCell(b, x, fy, D);
            // balcony strip line at z=D
            for (int x = 4; x <= W - 2; x++) b.set(x, fy + 1, D, "minecraft:white_concrete");
            for (int x = 4; x <= W - 2; x++) { b.set(x, fy + 2, D, "minecraft:glass_pane", Map.of("east", "false", "west", "false", "north", "false", "south", "false", "waterlogged", "false")); }
            // walls around perimeter of living area (x3..W,z0..D), glass strip
            for (int x = 3; x <= W; x++) { b.set(x, fy + 1, 0, "minecraft:white_concrete"); b.set(x, fy + 1, D, "minecraft:white_concrete"); }
            for (int z = 0; z <= D; z++) { b.set(W, fy + 1, z, "minecraft:white_concrete"); }
            for (int x = 3; x <= W; x++) for (int z = 1; z <= D - 1; z = z + 1)
                if ((x + z) % 3 == 0) b.set(x, fy + 2, z, "minecraft:glass"); // window dot
        }
        // vertical glass accents on facade (x=W)
        for (int y = 2; y <= H - 1; y++) if (y % 3 != 0) b.set(W, y, 3, "minecraft:glass");
        // entrance
        b.set(6, 1, 0, "minecraft:dark_oak_door", Map.of("facing", "north", "half", "lower", "hinge", "left", "open", "false", "powered", "false"));
        b.set(6, 2, 0, "minecraft:dark_oak_door", Map.of("facing", "north", "half", "upper", "hinge", "left", "open", "false", "powered", "false"));
        b.set(7, 1, 0, "minecraft:lantern", Map.of("hanging", "false", "waterlogged", "false"));
        // spiral ladder in core
        for (int y = 2; y <= H - 2; y++) b.set(1, y, 1, "minecraft:ladder", Map.of("facing", "north", "waterlogged", "false"));
    }
    static void setFloorCell(B b, int x, int fy, int D) {
        for (int z = 0; z <= D; z++) if (!((x == 6 || x == 7) && z == 2)) b.set(x, fy, z, "minecraft:smooth_quartz");
    }

    // --- 3) modern office skyscraper: glass curtain wall tower
    static void genOffice(B b) {
        int W = 11, D = 11, H = 30;
        b.box(0, 0, 0, W, 0, D, "minecraft:stone");
        b.frame(0, 1, 0, W, H - 1, D, "minecraft:smooth_quartz");
        // glass skin with concrete floor lines every 3
        for (int y = 1; y <= H - 1; y++) {
            int band = y % 3;
            for (int x = 1; x <= W - 1; x++) {
                b.set(x, y, 0, band == 0 ? "minecraft:light_gray_concrete" : "minecraft:glass");
                b.set(x, y, D, band == 0 ? "minecraft:light_gray_concrete" : "minecraft:glass");
            }
            for (int z = 1; z <= D - 1; z++) {
                b.set(0, y, z, band == 0 ? "minecraft:light_gray_concrete" : "minecraft:glass");
                b.set(W, y, z, band == 0 ? "minecraft:light_gray_concrete" : "minecraft:glass");
            }
        }
        // crown with sea lantern strip
        b.box(0, H, 0, W, H + 1, D, "minecraft:light_gray_concrete");
        for (int x = 1; x <= W - 1; x++) for (int z = 1; z <= D - 1; z++) b.set(x, H + 1, z, "minecraft:sea_lantern");
        // lobby glass ground floor + door
        for (int x = 2; x <= W - 2; x++) { b.set(x, 1, 0, "minecraft:glass"); b.set(x, 2, 0, "minecraft:glass"); }
        b.set(5, 1, 0, "minecraft:iron_door", Map.of("facing", "north", "half", "lower", "hinge", "left", "open", "false", "powered", "false"));
        b.set(5, 2, 0, "minecraft:iron_door", Map.of("facing", "north", "half", "upper", "hinge", "left", "open", "false", "powered", "false"));
    }

    // --- 4) modern gas station / car showroom
    static void genShowroom(B b) {
        int W = 15, D = 9, H = 6;
        b.box(0, 0, 0, W, 0, D, "minecraft:light_gray_concrete");
        b.box(0, 0, D + 1, W, 0, D + 2, "minecraft:white_concrete");
        // glass showroom box
        b.frame(0, 1, 0, W, H - 1, D, "minecraft:white_concrete");
        for (int y = 1; y <= H - 1; y++) for (int x = 1; x <= W - 1; x++) b.set(x, y, D, "minecraft:glass");
        for (int y = 1; y <= H - 1; y++) for (int x = 1; x <= W - 1; x++) b.set(x, y, 0, "minecraft:white_concrete");
        // roof floating slab (cantilever) + skylight
        b.box(0, H, 0, W, H, D + 2, "minecraft:white_concrete");
        for (int x = 3; x <= W - 3; x++) { b.set(x, H + 1, 3, "minecraft:glass"); b.set(x, H + 1, 4, "minecraft:glass"); b.set(x, H + 1, D - 3, "minecraft:glass"); b.set(x, H + 1, D - 2, "minecraft:glass"); }
        // red accent strip
        for (int x = 1; x <= W - 1; x++) { b.set(x, 4, 0, "minecraft:red_concrete"); }
        // door
        b.set(1, 1, 8, "minecraft:dark_oak_door", Map.of("facing", "south", "half", "lower", "hinge", "right", "open", "false", "powered", "false"));
        b.set(1, 2, 8, "minecraft:dark_oak_door", Map.of("facing", "south", "half", "upper", "hinge", "right", "open", "false", "powered", "false"));
        // pumps: 3 columns near road with red bands and sea lantern strip lights
        for (int j = 0; j < 3; j++) {
            int x = 3 + j * 4;
            b.set(x, 1, D + 1, "minecraft:stone");
            b.set(x, 2, D + 1, "minecraft:red_concrete");
            b.set(x, 3, D + 1, "minecraft:stone");
            b.set(x, 4, D + 1, "minecraft:red_concrete");
            b.set(x, H, D + 1, "minecraft:sea_lantern");
        }
    }

    // --- 5) modern shopping pavilion: two glazed wings + atrium, rooftop plaza
    static void genMall(B b) {
        int W = 21, D = 15, H = 8;
        b.box(0, 0, 0, W, 0, D, "minecraft:white_concrete");
        // hollow atrium: perimeter light_gray_concrete walls only (y=1..H-1)
        b.box(0, 1, 0, W, H - 1, 0, "minecraft:light_gray_concrete");
        b.box(0, 1, D, W, H - 1, D, "minecraft:light_gray_concrete");
        b.box(0, 1, 0, 0, H - 1, D, "minecraft:light_gray_concrete");
        b.box(W, 1, 0, W, H - 1, D, "minecraft:light_gray_concrete");
        // glass atrium roof
        b.box(2, H, 2, W - 2, H, D - 2, "minecraft:glass");
        b.box(2, H, 2, W - 2, H, 2, "minecraft:white_concrete");
        b.box(2, H, D - 2, W - 2, H, D - 2, "minecraft:white_concrete");
        b.box(2, H, 2, 2, H, D - 2, "minecraft:white_concrete");
        b.box(W - 2, H, 2, W - 2, H, D - 2, "minecraft:white_concrete");
        for (int y = 2; y <= H - 1; y++) { b.set(1, y, 1, "minecraft:white_concrete"); b.set(1, y, D - 1, "minecraft:white_concrete"); b.set(W - 1, y, 1, "minecraft:white_concrete"); b.set(W - 1, y, D - 1, "minecraft:white_concrete"); }
        for (int y = 2; y <= H - 1; y++) for (int x = 2; x <= W - 2; x++) { b.set(x, y, 1, "minecraft:glass"); b.set(x, y, D - 1, "minecraft:glass"); }
        for (int y = 2; y <= H - 1; y++) for (int z = 2; z <= D - 2; z++) { b.set(1, y, z, "minecraft:glass"); b.set(W - 1, y, z, "minecraft:glass"); }
        // ground floor white concrete walls under
        for (int x = 0; x <= W; x++) { b.set(x, 1, 0, "minecraft:white_concrete"); b.set(x, 1, D, "minecraft:white_concrete"); }
        for (int z = 0; z <= D; z++) { b.set(0, 1, z, "minecraft:white_concrete"); b.set(W, 1, z, "minecraft:white_concrete"); }
        // floor lamps inside (sea lantern columns)
        for (int z = 3; z <= D - 3; z += 4) { b.set(10, 1, z, "minecraft:iron_block"); b.set(10, 2, z, "minecraft:sea_lantern"); }
        // entrances (4 doors)
        for (int x : new int[]{ 5, 10, 15 }) b.set(x, 1, 0, "minecraft:glass", Map.of("waterlogged", "false"));
        b.set(10, 1, 0, "minecraft:dark_oak_door", Map.of("facing", "north", "half", "lower", "hinge", "right", "open", "false", "powered", "false"));
        b.set(10, 2, 0, "minecraft:dark_oak_door", Map.of("facing", "north", "half", "upper", "hinge", "right", "open", "false", "powered", "false"));
        // rooftop terrace edge railings (glass panes)
        for (int x = 2; x <= W - 2; x++) { b.set(x, H + 1, 2, "minecraft:glass_pane", Map.of("east", "false", "west", "false", "north", "false", "south", "false", "waterlogged", "false")); b.set(x, H + 1, D - 2, "minecraft:glass_pane", Map.of("east", "false", "west", "false", "north", "false", "south", "false", "waterlogged", "false")); }
        for (int z = 2; z <= D - 2; z++) { b.set(2, H + 1, z, "minecraft:glass_pane", Map.of("east", "false", "west", "false", "north", "false", "south", "false", "waterlogged", "false")); b.set(W - 2, H + 1, z, "minecraft:glass_pane", Map.of("east", "false", "west", "false", "north", "false", "south", "false", "waterlogged", "false")); }
        b.set(10, H + 1, 7, "minecraft:lantern", Map.of("hanging", "true", "waterlogged", "false"));
        b.set(10, H + 1, 7, "minecraft:sea_lantern");
    }

    static void generateAll(String outDir) throws IOException {
        B v = new B(); genVilla(v); v.write(outDir + "/modern_villa.nbt");
        B a = new B(); genApartment(a); a.write(outDir + "/modern_apartment.nbt");
        B o = new B(); genOffice(o); o.write(outDir + "/modern_office.nbt");
        B s = new B(); genShowroom(s); s.write(outDir + "/modern_showroom.nbt");
        B m = new B(); genMall(m); m.write(outDir + "/modern_mall.nbt");
    }

    static void slice(String path, int y) throws IOException {
        CompoundT root = readNbtFile(path, sniffGzip(path));
        int[] sz = intArr(root.map.get("size"));
        ListT pl = (ListT) root.map.get("palette");
        List<BlockState> pal = new ArrayList<>();
        for (Tag t : pl.v) pal.add(fromPaletteEntry((CompoundT) t));
        ListT bl = (ListT) root.map.get("blocks");
        char[][] grid = new char[sz[2]][sz[0]];
        for (int z = 0; z < sz[2]; z++) Arrays.fill(grid[z], '.');
        char[] light = new char[pal.size()];
        String[] keys = { "white_concrete", "white_terracotta", "glass", "quartz", "iron", "gray_concrete", "sea_lantern", "smooth_quartz", "stone_brick", "concrete", "wool" };
        char[] color = { 'W', 'w', 'g', 'Q', 'I', 'G', 'S', 'q', 'B', 'c', 'o' };
        char[] auto = { '#', '#', 'g', '#', 'I', '#', '#', '#', '#', '#', '#' };
        for (int i = 0; i < pal.size(); i++) {
            String id = pal.get(i).id;
            light[i] = '.';
            for (int k = 0; k < keys.length; k++) if (id.contains(keys[k])) { light[i] = color[k]; break; }
            if (light[i] == '.') light[i] = 'X';
        }
        for (Tag t : bl.v) {
            CompoundT c = (CompoundT) t;
            int[] pos = intArr(c.map.get("pos"));
            if (y >= 0 && pos[1] != y) continue;
            int state = ((IntT) c.map.get("state")).v;
            grid[pos[2]][pos[0]] = light[state];
        }
        System.out.println("size=" + Arrays.toString(sz) + " slice y=" + y);
        for (int z = 0; z < sz[2]; z++) System.out.println(new String(grid[z]));
        StringBuilder legend = new StringBuilder("legend: ");
        for (int i = 0; i < pal.size(); i++) legend.append(light[i]).append("=").append(pal.get(i).id).append("; ");
        System.out.println(legend);
    }

    static void levels(String path) throws IOException {
        CompoundT root = readNbtFile(path, sniffGzip(path));
        int[] sz = intArr(root.map.get("size"));
        ListT pl = (ListT) root.map.get("palette");
        int[] perLevel = new int[sz[1]];
        for (Tag t : ((ListT) root.map.get("blocks")).v) {
            CompoundT c = (CompoundT) t;
            int[] pos = intArr(c.map.get("pos"));
            if (pos[1] >= 0 && pos[1] < sz[1]) perLevel[pos[1]]++;
        }
        System.out.println(path);
        System.out.println("size=" + Arrays.toString(sz) + " total=" + Arrays.stream(perLevel).sum() + " palette=" + pl.v.size());
        for (int y = 0; y < sz[1]; y++) {
            double area = (double) perLevel[y] / (Math.max(1, sz[0] * sz[2])) * 100;
            System.out.printf("y=%2d %6d (%5.1f%%)%n", y, perLevel[y], area);
        }
    }

    static void jig(String path) throws IOException {
        CompoundT root = readNbtFile(path, sniffGzip(path));
        int[] sz = intArr(root.map.get("size"));
        ListT pl = (ListT) root.map.get("palette");
        List<BlockState> pal = new ArrayList<>();
        for (Tag t : pl.v) pal.add(fromPaletteEntry((CompoundT) t));
        ListT bl = (ListT) root.map.get("blocks");
        System.out.println("size=" + Arrays.toString(sz));
        for (Tag t : bl.v) {
            CompoundT c = (CompoundT) t;
            int state = ((IntT) c.map.get("state")).v;
            BlockState bs = pal.get(state);
            if (!bs.id.contains("jigsaw") && !bs.id.contains("structure_block") && !bs.id.contains("barrier") && !bs.id.contains("structure_void")) continue;
            System.out.println("  " + bs + " @ " + Arrays.toString(intArr(c.map.get("pos"))));
            if (c.map.get("nbt") instanceof CompoundT n) System.out.println("      nbt=" + n.map);
        }
    }

    static void tree(String path) throws IOException {
        CompoundT root = readNbtFile(path, sniffGzip(path));
        dump("", root, "root", 0);
    }
    static void dump(String kv, Tag t, String name, int depth) {
        String ind = "  ".repeat(depth);
        if (t instanceof CompoundT c) {
            System.out.println(ind + (name.isEmpty() ? "" : name + "=") + "{} " + kv);
            for (Map.Entry<String, Tag> e : c.map.entrySet()) dump("", e.getValue(), e.getKey(), depth + 1);
        } else if (t instanceof ListT l) {
            System.out.println(ind + name + "=[" + l.v.size() + " x type " + l.type + "]");
            int shown = 0;
            for (Tag e : l.v) { if (shown++ >= 3) { System.out.println(ind + "  ..."); break; } dump("", e, name + "[]", depth + 1); }
        } else if (t instanceof IntArrayT a) System.out.println(ind + name + "=int[" + a.v.length + "]");
        else if (t instanceof LongArrayT a) System.out.println(ind + name + "=long[" + a.v.length + "]");
        else if (t instanceof ByteArrayT a) System.out.println(ind + name + "=byte[" + a.v.length + "]");
        else if (t instanceof StrT s) System.out.println(ind + name + "=\"" + (s.v.length() > 60 ? s.v.substring(0,60) + "..." : s.v) + "\"");
        else if (t instanceof IntT i) System.out.println(ind + name + "=int " + i.v);
        else if (t instanceof LongT l) System.out.println(ind + name + "=long " + l.v);
        else if (t instanceof ByteT b) System.out.println(ind + name + "=byte " + b.v);
        else System.out.println(ind + name + "=" + t.getClass().getSimpleName());
    }

    // ---------------------------------------------------------------- mode: btower (generate BattleTowers-style tower as vanilla structure)

    static final String AIR = "minecraft:air";

    record TowerMat(String name, String wall, String light, String floor, String stair) {}

    static void btower(String outDir) throws IOException {
        List<TowerMat> mats = List.of(
                new TowerMat("battle_tower_cobblestone", "minecraft:cobblestone", "minecraft:torch", "minecraft:smooth_stone", "minecraft:stone_stairs"),
                new TowerMat("battle_tower_mossy", "minecraft:mossy_cobblestone", "minecraft:torch", "minecraft:smooth_stone", "minecraft:stone_stairs"),
                new TowerMat("battle_tower_sandstone", "minecraft:sandstone", "minecraft:torch", "minecraft:sandstone", "minecraft:sandstone_stairs"),
                new TowerMat("battle_tower_ice", "minecraft:ice", "", "minecraft:clay", "minecraft:oak_stairs"),
                new TowerMat("battle_tower_stone", "minecraft:stone", "minecraft:torch", "minecraft:smooth_stone", "minecraft:stone_stairs"),
                new TowerMat("battle_tower_netherrack", "minecraft:netherrack", "minecraft:glowstone", "minecraft:soul_sand", "minecraft:nether_brick_stairs"));
        int FLOORH = 7;
        int floorCount = 8;
        for (TowerMat m : mats) {
            Map<String, SBlockRef> cells = new HashMap<>();
            for (int f = 1; f <= floorCount; f++) {
                int baseY = (f - 1) * FLOORH;
                boolean topFloor = (f == floorCount);
                for (int fixture = 0; fixture < FLOORH; fixture++) {
                    int fi = (f == 1 && fixture < 4) ? 4 : fixture;
                    for (int x = -7; x < 7; x++) for (int z = -7; z < 7; z++) {
                        if (z == -7) {
                            if (x > -5 && x < 4) wall(cells, x, baseY + fi, z, m.wall);
                            continue;
                        }
                        if (z == -6 || z == -5) {
                            if (x == -5 || x == 4) { wall(cells, x, baseY + fi, z, m.wall); continue; }
                            if (z == -6) {
                                if (x == (fi + 1) % 7 - 3) {
                                    if (fi == 5) set(cells, x - 7, baseY + fi, z, m.floor);
                                    if (fi == 6 && topFloor) wall(cells, x, baseY + fi, z, m.wall);
                                    else stair(cells, x, baseY + fi, z, m.stair);
                                } else if (x > -5 && x < 4) {
                                    set(cells, x, baseY + fi, z, AIR);
                                }
                                continue;
                            }
                            if (x <= -5 || x >= 5) continue;
                            if ((fi == 0 || fi == 6) && (x == -4 || x == 3)) {
                                set(cells, x, baseY + fi, z, AIR);
                            } else if (fi == 5 && (x == 3 || x == -4)) {
                                set(cells, x, baseY + fi, z, m.floor);
                            } else {
                                wall(cells, x, baseY + fi, z, m.wall);
                            }
                            continue;
                        }
                        if (z == -4 || z == -3 || z == 2 || z == 3) {
                            if (x == -6 || x == 5) { wall(cells, x, baseY + fi, z, m.wall); continue; }
                            if (x <= -6 || x >= 5) continue;
                            set(cells, x, baseY + fi, z, fi == 5 ? m.floor : AIR);
                            continue;
                        }
                        if (z > -3 && z < 2) {
                            if (x == -7 || x == 6) {
                                boolean window = fi >= 0 && fi <= 3 && (z == -1 || z == 0);
                                if (window) set(cells, x, baseY + fi, z, AIR);
                                else wall(cells, x, baseY + fi, z, m.wall);
                                continue;
                            }
                            if (x <= -7 || x >= 6) continue;
                            set(cells, x, baseY + fi, z, fi == 5 ? m.floor : AIR);
                            continue;
                        }
                        if (z == 4) {
                            if (x == -5 || x == 4) { wall(cells, x, baseY + fi, z, m.wall); continue; }
                            if (x <= -5 || x >= 4) continue;
                            set(cells, x, baseY + fi, z, fi == 5 ? m.floor : AIR);
                            continue;
                        }
                        if (z == 5) {
                            if (x == -4 || x == -3 || x == 2 || x == 3) { wall(cells, x, baseY + fi, z, m.wall); continue; }
                            if (x <= -3 || x >= 2) continue;
                            set(cells, x, baseY + fi, z, fi == 5 ? m.floor : m.wall);
                            continue;
                        }
                        if (z == 6) {
                            if (x > -3 && x < 2) wall(cells, x, baseY + fi, z, m.wall);
                        }
                    }
                }
                if (f == 2) {
                    wall(cells, 3, baseY, -5, m.wall);
                    wall(cells, 3, baseY - 1, -5, m.wall);
                }
                if (!topFloor) {
                    setBlockRaw(cells, 2, baseY + 6, 2, "minecraft:mob_spawner", Map.of("id", "minecraft:mob_spawner", "SpawnData", Map.of("id", "minecraft:zombie")));
                    setBlockRaw(cells, -3, baseY + 6, 2, "minecraft:mob_spawner", Map.of("id", "minecraft:mob_spawner", "SpawnData", Map.of("id", "minecraft:zombie")));
                } else {
                    set(cells, 2, baseY + 6, 2, AIR);
                    set(cells, -3, baseY + 6, 2, AIR);
                }
                set(cells, 0, baseY + 6, 3, m.floor);
                set(cells, -1, baseY + 6, 3, m.floor);
                for (int c = 0; c < 2; c++) {
                    setBlockRaw(cells, -c, baseY + 7, 3, "minecraft:chest", Map.of("id", "minecraft:chest", "LootTable", "minecraft:chests/simple_dungeon"));
                }
                if (!m.light.isEmpty()) {
                    set(cells, 3, baseY + 2, -6, m.light);
                    set(cells, -4, baseY + 2, -6, m.light);
                    set(cells, 1, baseY + 2, -4, m.light);
                    set(cells, -2, baseY + 2, -4, m.light);
                }
            }
            StructureModel acc = new StructureModel();
            for (SBlockRef r : cells.values()) {
                SBlock sb = new SBlock();
                sb.x = r.x + 7; sb.y = r.y; sb.z = r.z + 7;
                BlockState bs = new BlockState(r.id, Map.of());
                sb.state = acc.stateIndex(bs);
                if (r.nbt != null) {
                    Map<String, Object> n = new LinkedHashMap<>();
                    for (var e : r.nbt.entrySet()) n.put(e.getKey(), nbtFromObject(e.getValue()));
                    sb.nbt = n;
                }
                acc.blocks.add(sb);
            }
            acc.bounds();
            acc.writeStructures(new File(outDir, m.name + ".nbt").getPath(), 4189);
            System.out.println("Tower " + m.name + " size=" + acc.sizeX + "x" + acc.sizeY + "x" + acc.sizeZ + " blocks=" + acc.blocks.size() + " palette=" + acc.palette.size());
        }
    }

    record SBlockRef(int x, int y, int z, String id, Map<String, Object> nbt) {}

    static void wall(Map<String, SBlockRef> cells, int x, int y, int z, String id) {
        cells.put(x + "," + y + "," + z, new SBlockRef(x, y, z, id, null));
        if (y == 4) { // floor 1, fixture 4: foundation column down to y=0
            for (int k = 0; k < 4; k++) cells.put(x + "," + k + "," + z, new SBlockRef(x, k, z, id, null));
        }
    }
    static void stair(Map<String, SBlockRef> cells, int x, int y, int z, String id) {
        cells.put(x + "," + y + "," + z, new SBlockRef(x, y, z, id, null));
    }
    static void set(Map<String, SBlockRef> cells, int x, int y, int z, String id) {
        cells.put(x + "," + y + "," + z, new SBlockRef(x, y, z, id, null));
    }
    static void setBlockRaw(Map<String, SBlockRef> cells, int x, int y, int z, String id, Map<String, Object> nbt) {
        cells.put(x + "," + y + "," + z, new SBlockRef(x, y, z, id, nbt));
    }
    static Tag nbtFromObject(Object o) {
        if (o instanceof String s) return new StrT(s);
        if (o instanceof Integer i) return new IntT(i);
        if (o instanceof Map<?, ?> mm) {
            CompoundT c = new CompoundT();
            for (Map.Entry<?, ?> e : mm.entrySet()) c.map.put(String.valueOf(e.getKey()), nbtFromObject(e.getValue()));
            return c;
        }
        return new StrT(String.valueOf(o));
    }

    // ---------------------------------------------------------------- mode: sfloor (1-block smooth_stone plate under each spawner, full interior width)

    static void sfloor(String inPath, String outPath) throws IOException {
        CompoundT root = readNbtFile(inPath, sniffGzip(inPath));
        int[] sz = intArr(root.map.get("size"));
        ListT pl = (ListT) root.map.get("palette");
        List<BlockState> pal = new ArrayList<>();
        for (Tag t : pl.v) pal.add(fromPaletteEntry((CompoundT) t));
        StructureModel acc = new StructureModel();
        Set<String> occ = new HashSet<>();
        Set<Integer> spawnerYs = new TreeSet<>();
        Map<String, SBlock> byKey = new HashMap<>();
        for (Tag t : ((ListT) root.map.get("blocks")).v) {
            CompoundT c = (CompoundT) t;
            int state = ((IntT) c.map.get("state")).v;
            BlockState bs = pal.get(state);
            int[] pos = intArr(c.map.get("pos"));
            SBlock sb = new SBlock();
            sb.x = pos[0]; sb.y = pos[1]; sb.z = pos[2];
            sb.state = acc.stateIndex(bs);
            sb.nbt = c.map.get("nbt") instanceof CompoundT n ? (Map) n.map : null;
            acc.blocks.add(sb);
            byKey.put(sb.x + "," + sb.y + "," + sb.z, sb);
            if (bs.id.contains("spawner")) spawnerYs.add(sb.y);
        }
        for (SBlock b : acc.blocks) {
            if (!pal.get(b.state).id.equals("minecraft:air")) occ.add(b.x + "," + b.y + "," + b.z);
        }
        int fill = acc.stateIndex(new BlockState("minecraft:smooth_stone", Map.of()));
        int added = 0;
        for (int sy : spawnerYs) {
            int fy = sy - 1;
            for (int x = 1; x < sz[0] - 1; x++) for (int z = 1; z < sz[2] - 1; z++) {
                String k = x + "," + fy + "," + z;
                if (occ.contains(k)) continue;
                SBlock ex = byKey.get(k);
                if (ex != null) { acc.blocks.remove(ex); } // drop explicit air cell we are covering
                SBlock sb = new SBlock();
                sb.x = x; sb.y = fy; sb.z = z; sb.state = fill;
                acc.blocks.add(sb); occ.add(k); added++;
            }
        }
        // rebuild palette indexes to drop air removal side effects
        acc.bounds();
        acc.writeStructures(outPath, 4189);
        System.out.println("Sfloor " + outPath + " spawnerYs=" + spawnerYs + " platesAdded=" + added +
                " size=" + acc.sizeX + "x" + acc.sizeY + "x" + acc.sizeZ + " blocks=" + acc.blocks.size());
    }

    static void dbg(String inPath) throws IOException {
        CompoundT root = readNbtFile(inPath, sniffGzip(inPath));
        CompoundT s = root.map.get("Schematic") instanceof CompoundT sc ? sc : root;
        int w = num(s.map.getOrDefault("Width", new IntT(0)));
        int h = num(s.map.getOrDefault("Height", new IntT(0)));
        int l = num(s.map.getOrDefault("Length", new IntT(0)));
        long vol = (long) w * h * l;
        System.out.println("root keys=" + root.map.keySet());
        if (root != s) System.out.println("Schematic keys=" + s.map.keySet());
        System.out.println("w=" + w + " h=" + h + " l=" + l + " volume=" + vol);
        if (s.map.get("Blocks") instanceof CompoundT blk) {
            System.out.println("Blocks keys=" + blk.map.keySet() + " data.len=" +
                    (blk.map.get("Data") instanceof ByteArrayT ba ? ba.v.length : -1) +
                    " palette entries=" + (blk.map.get("Palette") instanceof CompoundT pc ? pc.map.size() : -1));
        } else {
            System.out.println("BlockData.len=" + (s.map.get("BlockData") instanceof ByteArrayT ba ? ba.v.length : -1) +
                    " palette entries=" + (s.map.get("Palette") instanceof CompoundT pc ? pc.map.size() : -1));
        }
    }
}
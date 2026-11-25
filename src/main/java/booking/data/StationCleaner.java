package booking.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Phase 3 Step 2: cleans data/stations.json (DataMeet, CC0) with the Section 6.5 rules and the
 * hand-checked whitelist data/major_stations.csv, then writes sql/stations_seed.sql and one log in evidence/.
 * Reads ONLY data/stations.json and data/major_stations.csv. Writes ONLY sql/stations_seed.sql and
 * evidence/phase3-stations-clean-<UTC>.log. Never connects to a database (no java.sql, no DataSource).
 * On any rule failure it writes nothing and exits with code 1.
 * Run: mvn -q compile exec:java "-Dexec.mainClass=booking.data.StationCleaner"
 */
public final class StationCleaner {

    static final Path SOURCE    = Path.of("data", "stations.json");
    static final Path WHITELIST = Path.of("data", "major_stations.csv");
    static final Path SEED      = Path.of("sql", "stations_seed.sql");
    static final Path EVIDENCE  = Path.of("evidence");

    static final Pattern CODE = Pattern.compile("^[A-Z]{1,5}$");
    static final Pattern KEYWORDS = Pattern.compile(
            "siding|cabin|yard|goods|workshop|colliery|depot|block hut|flag station", Pattern.CASE_INSENSITIVE);
    static final Set<String> VALID_ZONES = Set.of("NR", "WR", "NWR", "SR", "SCR", "SWR", "NER", "ER", "NFR",
            "ECR", "CR", "WCR", "NCR", "SECR", "SER", "ECoR", "KR");
    static final int MAX_NAME = 100;   // stations.name is VARCHAR(100)

    /** Null-safe: Set.of(...).contains(null) throws, and 4,532 source zones are null. */
    static boolean validZone(String z) {
        return z != null && VALID_ZONES.contains(z);
    }

    record Station(String code, String name, String zone) {}
    record Pick(String code, String name, String zone, boolean zoneFilled) {}

    public static void main(String[] args) throws Exception {
        List<String> log = new ArrayList<>();
        try {
            run(log);
        } catch (CleaningException e) {
            System.err.println("FAILED: " + e.getMessage() + " (nothing written)");
            System.exit(1);
        }
    }

    static void run(List<String> log) throws IOException, CleaningException {
        String stamp = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        byte[] raw = Files.readAllBytes(SOURCE);
        String sha = sha256(raw);
        log.add("ADDS Phase 3 Step 2: station cleaning, UTC " + stamp);
        log.add("source " + SOURCE + " bytes=" + raw.length + " sha256=" + sha + " (DataMeet railways, CC0)");

        // Step 1: read features, keep only code, name, zone
        JsonNode features = new ObjectMapper().readTree(raw).path("features");
        if (!features.isArray()) throw new CleaningException("source has no features array");
        List<Station> rows = new ArrayList<>();
        for (JsonNode f : features) {
            JsonNode p = f.path("properties");
            rows.add(new Station(text(p, "code"), text(p, "name"), text(p, "zone")));
        }
        step(log, "1 read features", rows.size());

        // Step 2: code must match ^[A-Z]{1,5}$ (removes XX- placeholders)
        rows = filter(rows, s -> s.code() != null && CODE.matcher(s.code()).matches());
        step(log, "2 valid code ^[A-Z]{1,5}$", rows.size());

        // Step 3: drop name equal to code
        rows = filter(rows, s -> s.name() != null && !s.name().equals(s.code()));
        step(log, "3 name differs from code", rows.size());

        // Step 4: drop siding/cabin/yard/... names
        rows = filter(rows, s -> !KEYWORDS.matcher(s.name()).find());
        step(log, "4 keyword filter", rows.size());

        // Step 5: duplicate codes, keep the first
        Map<String, Station> byCode = new LinkedHashMap<>();
        for (Station s : rows) {
            if (byCode.putIfAbsent(s.code(), s) != null) log.add("  duplicate dropped: " + s.code() + " " + s.name());
        }
        step(log, "5 unique codes", byCode.size());

        // Step 6: zone normalisation ('?' and null mean missing; a missing zone does not drop a row)
        long missing = byCode.values().stream().filter(s -> !validZone(s.zone())).count();
        log.add(String.format("step 6 zones: valid=%d missing=%d (kept)", byCode.size() - missing, missing));

        // Step 7: whitelist
        List<Pick> picks = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<String> lines = Files.readAllLines(WHITELIST, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.get(0).equals("code,name,zone")) throw new CleaningException("whitelist header must be code,name,zone");
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) continue;
            if (line.contains("\"")) { errors.add("line " + (i + 1) + ": quotes not supported"); continue; }
            String[] c = line.split(",", -1);
            if (c.length != 3) { errors.add("line " + (i + 1) + ": expected 3 fields"); continue; }
            String code = c[0], wlName = c[1], wlZone = c[2];
            if (!seen.add(code)) { errors.add(code + ": duplicate in whitelist"); continue; }
            if (!validZone(wlZone)) { errors.add(code + ": whitelist zone '" + wlZone + "' not valid"); continue; }
            Station s = byCode.get(code);
            if (s == null) { errors.add(code + ": not in cleaned source (missing or removed by steps 2-5)"); continue; }
            if (!s.name().equals(wlName)) { errors.add(code + ": whitelist name '" + wlName + "' differs from source '" + s.name() + "'"); continue; }
            if (s.name().length() > MAX_NAME) { errors.add(code + ": name longer than " + MAX_NAME); continue; }
            if (s.name().chars().anyMatch(ch -> ch < 0x20 || ch == '\\')) { errors.add(code + ": name has control char or backslash"); continue; }
            boolean filled = !validZone(s.zone());
            if (!filled && !s.zone().equals(wlZone)) { errors.add(code + ": zone conflict source=" + s.zone() + " whitelist=" + wlZone); continue; }
            picks.add(new Pick(code, s.name(), wlZone, filled));
        }
        if (!errors.isEmpty()) {
            errors.forEach(e -> System.err.println("  " + e));
            throw new CleaningException(errors.size() + " whitelist error(s)");
        }
        long filledCount = picks.stream().filter(Pick::zoneFilled).count();
        step(log, "7 whitelist selected", picks.size());
        log.add("  zone from source=" + (picks.size() - filledCount) + " zone filled from whitelist=" + filledCount);
        Map<String, int[]> perZone = new TreeMap<>();
        for (Pick p : picks) {
            int[] z = perZone.computeIfAbsent(p.zone(), k -> new int[2]);
            z[0]++;
            if (p.zoneFilled()) z[1]++;
        }
        perZone.forEach((z, n) -> log.add(String.format("  %-5s %3d stations, %3d filled", z, n[0], n[1])));
        log.add("zones filled from whitelist (source zone missing):");
        picks.stream().filter(Pick::zoneFilled).forEach(p -> log.add("  " + p.code() + " " + p.name() + " -> " + p.zone()));

        // Build the seed in memory, then write both files only after every check passed
        StringBuilder sql = new StringBuilder();
        sql.append("-- ADDS stations seed, generated by booking.data.StationCleaner. Do not edit by hand.\n")
           .append("-- Source: DataMeet railways stations.json (CC0), sha256 ").append(sha).append('\n')
           .append("-- Codes and names are 2016 versions; ").append(filledCount)
           .append(" zones come from data/major_stations.csv because the source has none.\n")
           .append("-- Idempotent: ON CONFLICT (code) DO NOTHING.\n")
           .append("BEGIN;\n");
        for (Pick p : picks) {
            sql.append("INSERT INTO stations (code, name, zone) VALUES (")
               .append(lit(p.code())).append(", ").append(lit(p.name())).append(", ").append(lit(p.zone()))
               .append(") ON CONFLICT (code) DO NOTHING;\n");
        }
        sql.append("COMMIT;\n");

        Path logFile = EVIDENCE.resolve("phase3-stations-clean-" + stamp + ".log");
        log.add("wrote " + SEED + " (" + picks.size() + " INSERT lines) and " + logFile);
        Files.writeString(SEED, sql.toString(), StandardCharsets.UTF_8);                 // UTF-8, no BOM
        Files.writeString(logFile, String.join("\n", log) + "\n", StandardCharsets.UTF_8);
        log.forEach(System.out::println);
    }

    /** SQL string literal: wrap in single quotes and double every embedded single quote (standard SQL). */
    static String lit(String s) {
        return "'" + s.replace("'", "''") + "'";
    }

    static String text(JsonNode p, String field) {
        JsonNode n = p.get(field);
        return (n == null || n.isNull()) ? null : n.asText();
    }

    static List<Station> filter(List<Station> in, java.util.function.Predicate<Station> keep) {
        return in.stream().filter(keep).toList();
    }

    static void step(List<String> log, String label, int count) {
        log.add(String.format("step %-28s %5d", label, count));
    }

    static String sha256(byte[] b) throws IOException {
        try {
            return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    static final class CleaningException extends Exception {
        CleaningException(String m) { super(m); }
    }
}

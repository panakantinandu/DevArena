package com.devarena.gym;

import com.devarena.common.ApiException;
import com.devarena.common.Ids;
import com.devarena.gamification.XpService;
import com.devarena.users.UserDtos;
import com.devarena.users.UserDtos.PublicUser;
import com.devarena.users.UserRepository;
import com.devarena.users.UserService;
import com.devarena.users.UserSettings;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Brain Gym: short judgment puzzles with server-side scoring and calibration.
 *
 * <p>The daily run is the same five puzzles for every player on a given date (one per mode plus
 * a second estimation), so friends can compare scores. Practice runs drill one mode and prefer
 * puzzles you haven't seen recently.
 *
 * <p>Secrets (answers, clue bodies, option scores) are only returned for a puzzle after this run
 * has answered it. Clue bodies are returned one at a time as they're revealed, and their cost is
 * recorded server-side.
 */
@Service
public class GymService {
    public enum Mode { ESTIMATION, INCIDENT, REVIEW, TRADEOFF }

    static final int DAILY_XP_MAX = 40;
    static final int PRACTICE_XP_MAX = 20;
    static final int PRACTICE_SIZE = 4;

    public record AnswerView(JsonNode response, int score, boolean correct, int confidence, JsonNode solution,
                             JsonNode detail, String explanation) {}

    public record PuzzleView(UUID id, int position, String mode, String difficulty, String title, JsonNode payload,
                             Map<String, String> revealedClues, AnswerView answer) {}

    public record RunView(UUID id, String kind, String mode, LocalDate runDate, String status, Integer totalScore,
                          List<PuzzleView> puzzles) {}

    public record AnswerResult(PuzzleView puzzle, String runStatus, Integer totalScore, int xpAwarded) {}

    public record SkillScore(String key, String label, Integer score, int samples) {}

    public record CalibrationBucket(String label, int from, int to, int answers, Double accuracy) {}

    public record DailyEntry(PublicUser user, String status, Integer score, boolean you) {}

    public record RunSummary(UUID id, String kind, String mode, LocalDate runDate, String status, Integer totalScore,
                             java.time.Instant createdAt) {}

    public record Overview(LocalDate today, RunSummary daily, Map<String, Integer> puzzlesPerMode,
                           List<SkillScore> skills, List<CalibrationBucket> calibration, Integer calibrationScore,
                           int answeredTotal, List<DailyEntry> dailyBoard, List<RunSummary> recent) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final UserService users;
    private final UserRepository userRepo;
    private final XpService xp;
    private final Clock clock;

    public GymService(JdbcTemplate jdbc, ObjectMapper mapper, UserService users, UserRepository userRepo, XpService xp,
                      Clock clock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.users = users;
        this.userRepo = userRepo;
        this.xp = xp;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ runs

    /** Starts today's daily run, or returns the existing one (one per user per local date). */
    @Transactional
    public RunView daily(UUID userId) {
        LocalDate today = today(userId);
        UUID existing = dailyRunId(userId, today);
        if (existing != null) return run(userId, existing);
        List<UUID> picks = dailyPicks(today);
        if (picks.isEmpty()) throw ApiException.conflict("NO_PUZZLES", "No Brain Gym puzzles are loaded yet");
        UUID runId = Ids.newId();
        try {
            insertRun(runId, userId, "DAILY", today, null, picks);
        } catch (DataIntegrityViolationException raced) {
            return run(userId, dailyRunId(userId, today));
        }
        return run(userId, runId);
    }

    @Transactional
    public RunView practice(UUID userId, Mode mode) {
        List<UUID> picks = jdbc.queryForList("""
                select p.id from gym_puzzles p
                left join lateral (select max(a.answered_at) as last from gym_answers a
                                   where a.puzzle_id = p.id and a.user_id = ?) seen on true
                where p.mode = ?
                order by seen.last nulls first, random()
                limit ?
                """, UUID.class, userId, mode.name(), PRACTICE_SIZE);
        if (picks.isEmpty()) throw ApiException.conflict("NO_PUZZLES", "No puzzles for that mode yet");
        UUID runId = Ids.newId();
        insertRun(runId, userId, "PRACTICE", today(userId), mode.name(), picks);
        return run(userId, runId);
    }

    /**
     * Same five puzzles for everyone on a date: estimation, incident, review, trade-off and a second
     * estimation, chosen by a stable hash of the date.
     */
    List<UUID> dailyPicks(LocalDate date) {
        Map<String, List<UUID>> byMode = new HashMap<>();
        jdbc.query("select id, mode from gym_puzzles order by slug",
                rs -> { byMode.computeIfAbsent(rs.getString(2), k -> new ArrayList<>()).add(rs.getObject(1, UUID.class)); });
        long seed = date.toEpochDay();
        List<UUID> out = new ArrayList<>();
        String[] plan = {"ESTIMATION", "INCIDENT", "REVIEW", "TRADEOFF", "ESTIMATION"};
        for (int i = 0; i < plan.length; i++) {
            List<UUID> pool = byMode.getOrDefault(plan[i], List.of());
            if (pool.isEmpty()) continue;
            int idx = (int) Math.floorMod(mix(seed * 31 + plan[i].hashCode()), pool.size());
            UUID pick = pool.get(idx);
            // Second estimation: take the next one so the two differ.
            if (out.contains(pick) && pool.size() > 1) pick = pool.get((idx + 1) % pool.size());
            if (!out.contains(pick)) out.add(pick);
        }
        return out;
    }

    private void insertRun(UUID runId, UUID userId, String kind, LocalDate date, String mode, List<UUID> puzzles) {
        jdbc.update("""
                insert into gym_runs(id, user_id, kind, run_date, mode, status, created_at) values (?, ?, ?, ?, ?, 'IN_PROGRESS', ?)
                """, runId, userId, kind, date, mode, Timestamp.from(clock.instant()));
        for (int i = 0; i < puzzles.size(); i++) {
            jdbc.update("insert into gym_run_puzzles(run_id, puzzle_id, position) values (?, ?, ?)", runId, puzzles.get(i), i);
        }
    }

    @Transactional(readOnly = true)
    public RunView run(UUID userId, UUID runId) {
        Map<String, Object> r = ownedRun(userId, runId);
        List<PuzzleView> puzzles = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList("""
                select p.id, rp.position, p.mode, p.difficulty, p.title, p.payload::text as payload
                from gym_run_puzzles rp join gym_puzzles p on p.id = rp.puzzle_id
                where rp.run_id = ? order by rp.position
                """, runId)) {
            puzzles.add(puzzleView(runId, row));
        }
        return new RunView(runId, (String) r.get("kind"), (String) r.get("mode"), ((java.sql.Date) r.get("run_date")).toLocalDate(),
                (String) r.get("status"), (Integer) r.get("total_score"), puzzles);
    }

    private PuzzleView puzzleView(UUID runId, Map<String, Object> row) {
        UUID puzzleId = (UUID) row.get("id");
        Map<String, String> revealed = new LinkedHashMap<>();
        JsonNode secret = null;
        List<Map<String, Object>> ans = jdbc.queryForList("""
                select a.response::text as response, a.score, a.correct, a.confidence, p.secret::text as secret, p.explanation
                from gym_answers a join gym_puzzles p on p.id = a.puzzle_id where a.run_id = ? and a.puzzle_id = ?
                """, runId, puzzleId);
        List<String> clueIds = jdbc.queryForList("select clue_id from gym_clue_reveals where run_id = ? and puzzle_id = ? order by revealed_at",
                String.class, runId, puzzleId);
        if (!clueIds.isEmpty() || !ans.isEmpty()) {
            secret = mapper.readTree(jdbc.queryForObject("select secret::text from gym_puzzles where id = ?", String.class, puzzleId));
        }
        for (String c : clueIds) revealed.put(c, secret.path("clues").path(c).asString());
        AnswerView answer = null;
        if (!ans.isEmpty()) {
            Map<String, Object> a = ans.getFirst();
            JsonNode response = mapper.readTree((String) a.get("response"));
            answer = new AnswerView(response, (Integer) a.get("score"), (Boolean) a.get("correct"), (Integer) a.get("confidence"),
                    secret, detail((String) row.get("mode"), response, secret), (String) a.get("explanation"));
        }
        return new PuzzleView(puzzleId, (Integer) row.get("position"), (String) row.get("mode"), (String) row.get("difficulty"),
                (String) row.get("title"), mapper.readTree((String) row.get("payload")), revealed, answer);
    }

    // ------------------------------------------------------------------ clues & answers

    @Transactional
    public Map<String, String> revealClue(UUID userId, UUID runId, UUID puzzleId, String clueId) {
        ownedRun(userId, runId);
        Map<String, Object> p = runPuzzle(runId, puzzleId);
        if (!"INCIDENT".equals(p.get("mode"))) throw ApiException.badRequest("NO_CLUES", "This puzzle has no clues");
        if (answered(runId, puzzleId)) throw ApiException.conflict("ANSWERED", "Already answered");
        JsonNode secret = mapper.readTree((String) p.get("secret"));
        JsonNode body = secret.path("clues").path(clueId);
        if (body.isMissingNode()) throw ApiException.notFound("Clue");
        jdbc.update("""
                insert into gym_clue_reveals(run_id, puzzle_id, clue_id, revealed_at) values (?, ?, ?, ?) on conflict do nothing
                """, runId, puzzleId, clueId, Timestamp.from(clock.instant()));
        return Map.of("clueId", clueId, "body", body.asString());
    }

    @Transactional
    public AnswerResult answer(UUID userId, UUID runId, UUID puzzleId, JsonNode response, int confidence) {
        Map<String, Object> run = ownedRun(userId, runId);
        if (confidence < 50 || confidence > 99) throw ApiException.badRequest("BAD_CONFIDENCE", "Confidence must be 50–99%");
        Map<String, Object> p = runPuzzle(runId, puzzleId);
        String mode = (String) p.get("mode");
        JsonNode payload = mapper.readTree((String) p.get("payload"));
        JsonNode secret = mapper.readTree((String) p.get("secret"));
        GymScoring.Result result = score(mode, payload, secret, response, runId, puzzleId);

        int inserted = jdbc.update("""
                insert into gym_answers(run_id, puzzle_id, user_id, mode, response, score, correct, confidence, answered_at)
                values (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?) on conflict do nothing
                """, runId, puzzleId, userId, mode, mapper.writeValueAsString(sanitize(mode, response)), result.score(),
                result.correct(), confidence, Timestamp.from(clock.instant()));
        if (inserted == 0) throw ApiException.conflict("ANSWERED", "You already answered this puzzle");

        int xpAwarded = 0;
        String status = (String) run.get("status");
        Integer total = (Integer) run.get("total_score");
        Integer remaining = jdbc.queryForObject("""
                select count(*) from gym_run_puzzles rp where rp.run_id = ?
                  and not exists (select 1 from gym_answers a where a.run_id = rp.run_id and a.puzzle_id = rp.puzzle_id)
                """, Integer.class, runId);
        if (remaining != null && remaining == 0 && "IN_PROGRESS".equals(status)) {
            Double avg = jdbc.queryForObject("select avg(score) from gym_answers where run_id = ?", Double.class, runId);
            total = (int) Math.round(avg == null ? 0 : avg);
            jdbc.update("update gym_runs set status = 'COMPLETED', total_score = ?, completed_at = ? where id = ? and status = 'IN_PROGRESS'",
                    total, Timestamp.from(clock.instant()), runId);
            status = "COMPLETED";
            boolean daily = "DAILY".equals(run.get("kind"));
            int amount = (int) Math.round(total / 100.0 * (daily ? DAILY_XP_MAX : PRACTICE_XP_MAX));
            UserSettings s = users.settings(userId);
            String source = daily ? "daily:" + run.get("run_date") : "run:" + runId;
            if (amount > 0 && xp.award(userId, s.zone(), XpService.Source.GYM, source, amount,
                    daily ? "Brain Gym daily" : "Brain Gym practice")) {
                xpAwarded = amount;
            }
        }
        Map<String, Object> row = jdbc.queryForMap("""
                select p.id, rp.position, p.mode, p.difficulty, p.title, p.payload::text as payload
                from gym_run_puzzles rp join gym_puzzles p on p.id = rp.puzzle_id where rp.run_id = ? and rp.puzzle_id = ?
                """, runId, puzzleId);
        return new AnswerResult(puzzleView(runId, row), status, total, xpAwarded);
    }

    private GymScoring.Result score(String mode, JsonNode payload, JsonNode secret, JsonNode r, UUID runId, UUID puzzleId) {
        try {
            return switch (Mode.valueOf(mode)) {
                case ESTIMATION -> GymScoring.estimation(num(r, "value"), num(r, "low"), num(r, "high"),
                        secret.path("answer").asDouble());
                case INCIDENT -> {
                    String root = text(r, "rootCause");
                    String action = text(r, "action");
                    requireOption(payload.path("rootCauses"), root);
                    requireOption(payload.path("actions"), action);
                    int cost = 0;
                    List<String> revealed = jdbc.queryForList("select clue_id from gym_clue_reveals where run_id = ? and puzzle_id = ?",
                            String.class, runId, puzzleId);
                    for (JsonNode c : payload.path("clues")) {
                        if (revealed.contains(c.path("id").asString())) cost += c.path("cost").asInt();
                    }
                    yield GymScoring.incident(root.equals(secret.path("rootCause").asString()),
                            action.equals(secret.path("action").asString()), cost);
                }
                case REVIEW -> {
                    int lineCount = payload.path("code").asString().split("\n", -1).length;
                    List<Integer> flagged = new ArrayList<>();
                    for (JsonNode n : r.path("lines")) {
                        int line = n.asInt();
                        if (line < 1 || line > lineCount) throw ApiException.badRequest("BAD_LINE", "Line out of range");
                        flagged.add(line);
                    }
                    if (flagged.isEmpty()) throw ApiException.badRequest("NO_LINES", "Flag at least one line");
                    List<List<Integer>> bugs = new ArrayList<>();
                    for (JsonNode b : secret.path("bugs")) {
                        List<Integer> lines = new ArrayList<>();
                        b.path("lines").forEach(l -> lines.add(l.asInt()));
                        bugs.add(lines);
                    }
                    yield GymScoring.review(flagged, bugs);
                }
                case TRADEOFF -> {
                    String option = text(r, "option");
                    String risk = text(r, "risk");
                    requireOption(payload.path("options"), option);
                    requireOption(payload.path("risks"), risk);
                    int optScore = secret.path("options").path(option).path("score").asInt();
                    int best = 0;
                    for (JsonNode o : secret.path("options")) best = Math.max(best, o.path("score").asInt());
                    yield GymScoring.tradeoff(optScore, risk.equals(secret.path("bestRisk").asString()), optScore == best);
                }
            };
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("BAD_RESPONSE", e.getMessage());
        }
    }

    /** Keeps only the fields each mode uses, so arbitrary client JSON is never stored. */
    private JsonNode sanitize(String mode, JsonNode r) {
        ObjectNode out = mapper.createObjectNode();
        switch (Mode.valueOf(mode)) {
            case ESTIMATION -> { out.put("value", num(r, "value")); out.put("low", num(r, "low")); out.put("high", num(r, "high")); }
            case INCIDENT -> { out.put("rootCause", text(r, "rootCause")); out.put("action", text(r, "action")); }
            case REVIEW -> { var arr = out.putArray("lines"); r.path("lines").forEach(n -> arr.add(n.asInt())); }
            case TRADEOFF -> { out.put("option", text(r, "option")); out.put("risk", text(r, "risk")); }
        }
        return out;
    }

    /** Extra, computed feedback per mode (e.g. how far off an estimate was, which bugs were found). */
    private JsonNode detail(String mode, JsonNode response, JsonNode secret) {
        ObjectNode d = mapper.createObjectNode();
        if ("ESTIMATION".equals(mode)) {
            double truth = secret.path("answer").asDouble();
            double v = response.path("value").asDouble();
            double factor = v >= truth ? v / truth : truth / v;
            d.put("factorOff", Math.round(factor * 100) / 100.0);
            d.put("direction", v >= truth ? "over" : "under");
            d.put("rangeContainsTruth", response.path("low").asDouble() <= truth && truth <= response.path("high").asDouble());
        } else if ("REVIEW".equals(mode)) {
            var found = d.putArray("bugsFound");
            List<Integer> flags = new ArrayList<>();
            response.path("lines").forEach(n -> flags.add(n.asInt()));
            for (JsonNode b : secret.path("bugs")) {
                boolean hit = false;
                for (JsonNode l : b.path("lines")) hit |= flags.contains(l.asInt());
                found.add(hit);
            }
        }
        return d;
    }

    // ------------------------------------------------------------------ overview

    @Transactional(readOnly = true)
    public Overview overview(UUID userId) {
        LocalDate today = today(userId);
        UUID dailyId = dailyRunId(userId, today);
        RunSummary daily = dailyId == null ? null : summary(dailyId);

        Map<String, Integer> perMode = new LinkedHashMap<>();
        for (Mode m : Mode.values()) perMode.put(m.name(), 0);
        jdbc.query("select mode, count(*) from gym_puzzles group by mode", rs -> { perMode.put(rs.getString(1), rs.getInt(2)); });

        // Skills: average score per mode over the last 60 answers in that mode.
        Map<String, String> labels = Map.of("ESTIMATION", "Estimation", "INCIDENT", "Debugging", "REVIEW", "Code review",
                "TRADEOFF", "Judgment");
        List<SkillScore> skills = new ArrayList<>();
        for (Mode m : Mode.values()) {
            Map<String, Object> s = jdbc.queryForMap("""
                    select round(avg(score)) as avg, count(*) as n from (
                      select score from gym_answers where user_id = ? and mode = ? order by answered_at desc limit 60) x
                    """, userId, m.name());
            Number avg = (Number) s.get("avg");
            skills.add(new SkillScore(m.name(), labels.get(m.name()), avg == null ? null : avg.intValue(),
                    ((Number) s.get("n")).intValue()));
        }
        List<int[]> pairs = jdbc.query("""
                select confidence, correct from gym_answers where user_id = ? order by answered_at desc limit 200
                """, (rs, i) -> new int[]{rs.getInt(1), rs.getBoolean(2) ? 1 : 0}, userId);
        Integer calibrationScore = GymScoring.calibration(pairs);
        skills.add(new SkillScore("CALIBRATION", "Calibration", calibrationScore, pairs.size()));

        int[][] bounds = {{50, 59}, {60, 69}, {70, 79}, {80, 89}, {90, 99}};
        List<CalibrationBucket> buckets = new ArrayList<>();
        for (int[] b : bounds) {
            List<int[]> in = pairs.stream().filter(p -> p[0] >= b[0] && p[0] <= b[1]).toList();
            Double acc = in.isEmpty() ? null : in.stream().mapToInt(p -> p[1]).average().orElse(0);
            buckets.add(new CalibrationBucket(b[0] + "–" + b[1] + "%", b[0], b[1], in.size(), acc));
        }

        List<DailyEntry> board = jdbc.query("""
                with people as (
                    select ? as id
                    union select case when requester_id = ? then addressee_id else requester_id end
                    from friendships where status = 'ACCEPTED' and (requester_id = ? or addressee_id = ?)
                )
                select u.id, u.username, u.display_name, u.avatar_url, r.status, r.total_score
                from people pe join users u on u.id = pe.id
                left join gym_runs r on r.user_id = u.id and r.kind = 'DAILY' and r.run_date = ?
                order by r.total_score desc nulls last, u.display_name
                """, (rs, i) -> new DailyEntry(new PublicUser(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4)),
                rs.getString(5) == null ? "NOT_STARTED" : rs.getString(5),
                // Friends only see a score once that run is finished.
                "COMPLETED".equals(rs.getString(5)) ? (Integer) rs.getObject(6) : null,
                rs.getObject(1, UUID.class).equals(userId)), userId, userId, userId, userId, today);

        List<RunSummary> recent = jdbc.query("""
                select id, kind, mode, run_date, status, total_score, created_at from gym_runs
                where user_id = ? order by created_at desc limit 10
                """, (rs, i) -> new RunSummary(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                rs.getObject(4, LocalDate.class), rs.getString(5), (Integer) rs.getObject(6), rs.getTimestamp(7).toInstant()), userId);
        Integer answeredTotal = jdbc.queryForObject("select count(*) from gym_answers where user_id = ?", Integer.class, userId);
        return new Overview(today, daily, perMode, skills, buckets, calibrationScore, answeredTotal == null ? 0 : answeredTotal,
                board, recent);
    }

    // ------------------------------------------------------------------ helpers

    private RunSummary summary(UUID runId) {
        return jdbc.queryForObject("select id, kind, mode, run_date, status, total_score, created_at from gym_runs where id = ?",
                (rs, i) -> new RunSummary(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getObject(4, LocalDate.class), rs.getString(5), (Integer) rs.getObject(6), rs.getTimestamp(7).toInstant()), runId);
    }

    private LocalDate today(UUID userId) {
        return LocalDate.now(clock.withZone(users.settings(userId).zone()));
    }

    private UUID dailyRunId(UUID userId, LocalDate date) {
        List<UUID> ids = jdbc.queryForList("select id from gym_runs where user_id = ? and kind = 'DAILY' and run_date = ?",
                UUID.class, userId, date);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private Map<String, Object> ownedRun(UUID userId, UUID runId) {
        List<Map<String, Object>> rows = jdbc.queryForList("select * from gym_runs where id = ? and user_id = ?", runId, userId);
        if (rows.isEmpty()) throw ApiException.notFound("Run");
        return rows.getFirst();
    }

    private Map<String, Object> runPuzzle(UUID runId, UUID puzzleId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select p.mode, p.payload::text as payload, p.secret::text as secret
                from gym_run_puzzles rp join gym_puzzles p on p.id = rp.puzzle_id where rp.run_id = ? and rp.puzzle_id = ?
                """, runId, puzzleId);
        if (rows.isEmpty()) throw ApiException.notFound("Puzzle");
        return rows.getFirst();
    }

    private boolean answered(UUID runId, UUID puzzleId) {
        Integer n = jdbc.queryForObject("select count(*) from gym_answers where run_id = ? and puzzle_id = ?", Integer.class, runId, puzzleId);
        return n != null && n > 0;
    }

    private static void requireOption(JsonNode options, String id) {
        for (JsonNode o : options) if (o.path("id").asString().equals(id)) return;
        throw new IllegalArgumentException("Unknown choice: " + id);
    }

    private static double num(JsonNode r, String field) {
        JsonNode n = r.path(field);
        if (!n.isNumber()) throw new IllegalArgumentException("Missing number: " + field);
        double v = n.asDouble();
        if (Double.isNaN(v) || Double.isInfinite(v)) throw new IllegalArgumentException("Invalid number: " + field);
        return v;
    }

    private static String text(JsonNode r, String field) {
        JsonNode n = r.path(field);
        if (!n.isString() || n.asString().isBlank()) throw new IllegalArgumentException("Missing choice: " + field);
        return n.asString();
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

}

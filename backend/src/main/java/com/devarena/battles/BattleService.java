package com.devarena.battles;

import com.devarena.battles.BattleDtos.AnswerAck;
import com.devarena.battles.BattleDtos.BattleResults;
import com.devarena.battles.BattleDtos.BattleState;
import com.devarena.battles.BattleDtos.BattleView;
import com.devarena.battles.BattleDtos.CreateBattleRequest;
import com.devarena.battles.BattleDtos.HistoryItem;
import com.devarena.battles.BattleDtos.LiveQuestion;
import com.devarena.battles.BattleDtos.PlayerRoundResult;
import com.devarena.battles.BattleDtos.PlayerState;
import com.devarena.battles.BattleDtos.QuestionBreakdown;
import com.devarena.battles.BattleDtos.RoundReveal;
import com.devarena.common.ApiException;
import com.devarena.common.Difficulty;
import com.devarena.common.Ids;
import com.devarena.gamification.AchievementService;
import com.devarena.gamification.XpService;
import com.devarena.progress.MasteryService;
import com.devarena.questions.Question;
import com.devarena.questions.QuestionService;
import com.devarena.realtime.RealtimeHub;
import com.devarena.realtime.RoomAccess;
import com.devarena.users.User;
import com.devarena.users.UserDtos;
import com.devarena.users.UserDtos.PublicUser;
import com.devarena.users.UserRepository;
import com.devarena.users.UserService;
import com.devarena.users.UserSettings;
import jakarta.annotation.PreDestroy;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Server-authoritative quiz battles.
 *
 * <p>Every mutation of a battle happens while holding that battle's lock and inside a
 * database transaction, so timers, early round closes and answers can't interleave.
 * Scores are computed here from server timestamps; clients only send an option id.
 * Correct answers are revealed only after a round closes.
 *
 * <p>Locks and timers are in-memory, which is correct for the single-instance
 * deployment. After a restart, {@link #recover()} rebuilds the timers from the database.
 */
@Service
public class BattleService implements RoomAccess {
    private static final Logger log = LoggerFactory.getLogger(BattleService.class);

    static final int MAX_PLAYERS = 8;
    static final int BASE_POINTS = 100;
    static final int MAX_SPEED_BONUS = 25;
    static final long GRACE_MILLIS = 750;
    static final long REVEAL_MILLIS = 4_000;
    static final int XP_PER_CORRECT = 10;
    static final int XP_WIN = 50;

    private final QuizBattleRepository battles;
    private final BattleParticipantRepository participants;
    private final QuestionService questionService;
    private final UserRepository userRepo;
    private final UserService users;
    private final RealtimeHub hub;
    private final XpService xp;
    private final MasteryService mastery;
    private final AchievementService achievements;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final TransactionTemplate tx;

    private final Map<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();
    /** Highest round index whose result has been revealed, per battle. */
    private final Map<UUID, Integer> closedRound = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "battle-timer");
        t.setDaemon(true);
        return t;
    });
    private final SecureRandom random = new SecureRandom();

    public BattleService(QuizBattleRepository battles, BattleParticipantRepository participants,
                         QuestionService questionService, UserRepository userRepo, UserService users, RealtimeHub hub,
                         XpService xp, MasteryService mastery, AchievementService achievements, JdbcTemplate jdbc,
                         Clock clock, PlatformTransactionManager txManager) {
        this.battles = battles;
        this.participants = participants;
        this.questionService = questionService;
        this.userRepo = userRepo;
        this.users = users;
        this.hub = hub;
        this.xp = xp;
        this.mastery = mastery;
        this.achievements = achievements;
        this.jdbc = jdbc;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    // ------------------------------------------------------------------ RoomAccess

    @Override
    public String prefix() { return "battle"; }

    @Override
    public boolean canSubscribe(UUID userId, UUID battleId) {
        return participants.existsByBattleIdAndUserId(battleId, userId);
    }

    static String topic(UUID battleId) { return "battle:" + battleId; }

    // ------------------------------------------------------------------ lobby

    public BattleView create(UUID hostId, CreateBattleRequest req) {
        UUID battleId = tx.execute(st -> {
            List<UUID> picked = pickQuestions(req);
            QuizBattle b = new QuizBattle();
            b.setId(Ids.newId());
            b.setRoomCode(uniqueRoomCode());
            b.setHostId(hostId);
            b.setCategory(req.category());
            b.setDifficulty(req.difficulty());
            b.setQuestionCount(picked.size());
            b.setSecondsPerQuestion(req.secondsPerQuestion());
            battles.save(b);
            battles.flush();
            for (int i = 0; i < picked.size(); i++) {
                jdbc.update("insert into quiz_battle_questions(id, battle_id, question_id, position) values (?, ?, ?, ?)",
                        Ids.newId(), b.getId(), picked.get(i), i);
            }
            addParticipant(b.getId(), hostId);
            return b.getId();
        });
        return view(hostId, battleId);
    }

    private List<UUID> pickQuestions(CreateBattleRequest req) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select q.id, q.difficulty from questions q join topics t on t.id = q.topic_id
                where t.category = ? and q.status = 'ACTIVE'
                """, req.category().name());
        if (rows.size() < req.questionCount()) {
            throw ApiException.badRequest("NOT_ENOUGH_QUESTIONS",
                    "Only " + rows.size() + " questions exist for " + req.category().label() + ". Pick a smaller count.");
        }
        List<Map<String, Object>> shuffled = new ArrayList<>(rows);
        Collections.shuffle(shuffled, random);
        // Requested difficulty first, then the nearest difficulties to fill the set.
        shuffled.sort(Comparator.comparingInt(r ->
                Math.abs(Difficulty.valueOf((String) r.get("difficulty")).ordinal() - req.difficulty().ordinal())));
        return shuffled.stream().limit(req.questionCount()).map(r -> (UUID) r.get("id")).toList();
    }

    private String uniqueRoomCode() {
        for (int i = 0; i < 10; i++) {
            String code = Ids.roomCode(6);
            if (battles.findByRoomCode(code).isEmpty()) return code;
        }
        throw new IllegalStateException("Could not allocate a room code");
    }

    private void addParticipant(UUID battleId, UUID userId) {
        BattleParticipant p = new BattleParticipant();
        p.setId(Ids.newId());
        p.setBattleId(battleId);
        p.setUserId(userId);
        p.setJoinedAt(clock.instant());
        participants.save(p);
    }

    /** Minimal preview so someone holding a room code can see what they're joining. */
    public Map<String, Object> preview(String code) {
        QuizBattle b = battles.findByRoomCode(code.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("Battle room"));
        User host = userRepo.findById(b.getHostId()).orElseThrow();
        return Map.of("id", b.getId(), "status", b.getStatus(), "host", UserDtos.toPublic(host),
                "category", b.getCategory().label(), "difficulty", b.getDifficulty(), "questionCount", b.getQuestionCount(),
                "players", participants.findByBattleIdOrderByJoinedAt(b.getId()).size());
    }

    public BattleView join(UUID userId, String code) {
        QuizBattle found = battles.findByRoomCode(code.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("Battle room"));
        withLock(found.getId(), () -> tx.executeWithoutResult(st -> {
            QuizBattle b = battles.findById(found.getId()).orElseThrow();
            if (participants.existsByBattleIdAndUserId(b.getId(), userId)) return; // idempotent rejoin
            if (!b.getStatus().isOpenLobby()) throw ApiException.conflict("BATTLE_STARTED", "This battle has already started");
            if (participants.findByBattleIdOrderByJoinedAt(b.getId()).size() >= MAX_PLAYERS) {
                throw ApiException.conflict("ROOM_FULL", "This room is full");
            }
            addParticipant(b.getId(), userId);
            if (b.getStatus() == BattleStatus.READY) b.transitionTo(BattleStatus.WAITING);
        }));
        broadcast(found.getId(), "PLAYER_JOINED");
        return view(userId, found.getId());
    }

    public BattleView setReady(UUID userId, UUID battleId, boolean ready) {
        withLock(battleId, () -> tx.executeWithoutResult(st -> {
            QuizBattle b = requireBattle(battleId);
            BattleParticipant p = requireParticipant(battleId, userId);
            if (!b.getStatus().isOpenLobby()) throw ApiException.conflict("BATTLE_STARTED", "The battle has already started");
            p.setReady(ready);
            participants.flush();
            List<BattleParticipant> all = participants.findByBattleIdOrderByJoinedAt(battleId);
            boolean everyoneReady = all.size() >= 2 && all.stream().allMatch(BattleParticipant::isReady);
            b.transitionTo(everyoneReady ? BattleStatus.READY : BattleStatus.WAITING);
        }));
        broadcast(battleId, "PLAYER_READY");
        return view(userId, battleId);
    }

    public BattleView start(UUID userId, UUID battleId) {
        withLock(battleId, () -> {
            tx.executeWithoutResult(st -> {
                QuizBattle b = requireBattle(battleId);
                requireParticipant(battleId, userId);
                if (!b.getHostId().equals(userId)) throw ApiException.forbidden("Only the host can start the battle");
                if (b.getStatus() != BattleStatus.READY) {
                    throw ApiException.conflict("NOT_READY", "Everyone (at least two players) must be ready first");
                }
                b.transitionTo(BattleStatus.IN_PROGRESS);
                b.setStartedAt(clock.instant());
            });
            broadcast(battleId, "BATTLE_STARTED");
            openRound(battleId, 0);
        });
        return view(userId, battleId);
    }

    public void leave(UUID userId, UUID battleId) {
        withLock(battleId, () -> tx.executeWithoutResult(st -> {
            QuizBattle b = requireBattle(battleId);
            BattleParticipant p = requireParticipant(battleId, userId);
            if (!b.getStatus().isOpenLobby()) return; // mid-battle: you simply stop answering
            if (b.getHostId().equals(userId)) {
                b.transitionTo(BattleStatus.CANCELLED);
                b.setEndedAt(clock.instant());
            } else {
                participants.delete(p);
                participants.flush();
                if (b.getStatus() == BattleStatus.READY) b.transitionTo(BattleStatus.WAITING);
            }
        }));
        broadcast(battleId, "PLAYER_LEFT");
    }

    // ------------------------------------------------------------------ rounds

    private void openRound(UUID battleId, int index) {
        Instant[] endsAt = new Instant[1];
        withLock(battleId, () -> {
            Boolean opened = tx.execute(st -> {
                QuizBattle b = requireBattle(battleId);
                if (b.getStatus() != BattleStatus.IN_PROGRESS || b.getCurrentIndex() >= index) return false;
                Instant now = clock.instant();
                endsAt[0] = now.plusSeconds(b.getSecondsPerQuestion());
                b.setCurrentIndex(index);
                jdbc.update("update quiz_battle_questions set started_at = ?, ends_at = ? where battle_id = ? and position = ?",
                        Timestamp.from(now), Timestamp.from(endsAt[0]), battleId, index);
                return true;
            });
            if (Boolean.TRUE.equals(opened)) broadcast(battleId, "QUESTION_STARTED");
        });
        if (endsAt[0] != null) schedule(endsAt[0], () -> closeRound(battleId, index));
    }

    public AnswerAck answer(UUID userId, UUID battleId, int questionIndex, UUID optionId) {
        boolean[] everyoneAnswered = new boolean[1];
        withLock(battleId, () -> tx.executeWithoutResult(st -> {
            requireParticipant(battleId, userId);
            QuizBattle b = requireBattle(battleId);
            if (b.getStatus() != BattleStatus.IN_PROGRESS) throw ApiException.conflict("NOT_IN_PROGRESS", "The battle isn't running");
            if (b.getCurrentIndex() != questionIndex || isClosed(battleId, questionIndex)) {
                throw ApiException.conflict("ROUND_CLOSED", "That round has already ended");
            }
            Map<String, Object> bq = jdbc.queryForMap("""
                    select id, question_id, started_at, ends_at from quiz_battle_questions where battle_id = ? and position = ?
                    """, battleId, questionIndex);
            Instant now = clock.instant();
            Instant started = ((Timestamp) bq.get("started_at")).toInstant();
            Instant ends = ((Timestamp) bq.get("ends_at")).toInstant();
            if (now.isAfter(ends.plusMillis(GRACE_MILLIS))) throw ApiException.conflict("TIME_UP", "Time's up for this question");

            Question q = questionService.loadOrdered(List.of((UUID) bq.get("question_id"))).getFirst();
            boolean correct = questionService.grade(q, optionId).correct();
            long totalMs = Duration.between(started, ends).toMillis();
            long responseMs = Math.max(0, Math.min(totalMs, Duration.between(started, now).toMillis()));
            int points = points(correct, responseMs, totalMs);

            int inserted = jdbc.update("""
                    insert into quiz_answers(id, battle_question_id, user_id, option_id, correct, points, response_ms, answered_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?) on conflict (battle_question_id, user_id) do nothing
                    """, Ids.newId(), bq.get("id"), userId, optionId, correct, points, (int) responseMs, Timestamp.from(now));
            if (inserted == 0) throw ApiException.conflict("DUPLICATE_ANSWER", "You already answered this question");
            jdbc.update("""
                    update quiz_battle_participants set score = score + ?, correct_count = correct_count + ?
                    where battle_id = ? and user_id = ?
                    """, points, correct ? 1 : 0, battleId, userId);

            Integer answered = jdbc.queryForObject("select count(*) from quiz_answers where battle_question_id = ?",
                    Integer.class, bq.get("id"));
            everyoneAnswered[0] = answered != null && answered >= participants.findByBattleIdOrderByJoinedAt(battleId).size();
        }));
        // Tell everyone that this player answered; never whether they were right.
        hub.publish(topic(battleId), "ANSWER_SUBMITTED", Map.of("userId", userId, "questionIndex", questionIndex));
        if (everyoneAnswered[0]) closeRound(battleId, questionIndex);
        return new AnswerAck(questionIndex, true);
    }

    /** Server scoring: 100 for a correct answer plus up to 25 for speed, linear in time remaining. */
    static int points(boolean correct, long responseMs, long totalMs) {
        if (!correct) return 0;
        if (totalMs <= 0) return BASE_POINTS;
        double remaining = Math.max(0, totalMs - responseMs) / (double) totalMs;
        return BASE_POINTS + (int) Math.round(MAX_SPEED_BONUS * remaining);
    }

    void closeRound(UUID battleId, int index) {
        boolean[] last = new boolean[1];
        boolean[] closed = new boolean[1];
        withLock(battleId, () -> tx.executeWithoutResult(st -> {
            QuizBattle b = battles.findById(battleId).orElse(null);
            if (b == null || b.getStatus() != BattleStatus.IN_PROGRESS || b.getCurrentIndex() != index) return;
            if (isClosed(battleId, index)) return; // timer and "everyone answered" can both fire
            jdbc.update("update quiz_battle_questions set ends_at = least(ends_at, ?) where battle_id = ? and position = ?",
                    Timestamp.from(clock.instant()), battleId, index);
            closedRound.merge(battleId, index, Math::max);
            last[0] = index + 1 >= b.getQuestionCount();
            closed[0] = true;
        }));
        if (!closed[0]) return;
        broadcast(battleId, "ROUND_ENDED");
        Instant next = clock.instant().plusMillis(REVEAL_MILLIS);
        if (last[0]) schedule(next, () -> complete(battleId));
        else schedule(next, () -> openRound(battleId, index + 1));
    }

    private boolean isClosed(UUID battleId, int index) {
        return closedRound.getOrDefault(battleId, -1) >= index;
    }

    void complete(UUID battleId) {
        List<UUID> rewarded = new ArrayList<>();
        withLock(battleId, () -> tx.executeWithoutResult(st -> {
            QuizBattle b = battles.findById(battleId).orElse(null);
            if (b == null || b.getStatus() != BattleStatus.IN_PROGRESS) return;
            List<BattleParticipant> ps = participants.findByBattleIdOrderByJoinedAt(battleId);
            int top = ps.stream().mapToInt(BattleParticipant::getScore).max().orElse(0);
            List<BattleParticipant> leaders = ps.stream().filter(p -> p.getScore() == top).toList();
            b.transitionTo(BattleStatus.COMPLETED);
            b.setEndedAt(clock.instant());
            // A tie, or nobody scoring, has no winner.
            b.setWinnerId(leaders.size() == 1 && top > 0 ? leaders.getFirst().getUserId() : null);
            for (BattleParticipant p : ps) {
                reward(b, p);
                rewarded.add(p.getUserId());
            }
        }));
        if (rewarded.isEmpty()) return;
        broadcast(battleId, "BATTLE_COMPLETED");
        closedRound.remove(battleId);
        locks.remove(battleId);
    }

    /** Records attempts and awards XP once per answer and once per win. Runs inside complete()'s transaction. */
    private void reward(QuizBattle b, BattleParticipant p) {
        UserSettings s = users.settings(p.getUserId());
        List<Map<String, Object>> answers = jdbc.queryForList("""
                select a.correct, a.option_id, a.answered_at, bq.id as bq_id, bq.question_id, q.topic_id
                from quiz_answers a join quiz_battle_questions bq on bq.id = a.battle_question_id
                join questions q on q.id = bq.question_id
                where bq.battle_id = ? and a.user_id = ?
                """, b.getId(), p.getUserId());
        Set<UUID> topics = new HashSet<>();
        for (Map<String, Object> a : answers) {
            boolean correct = (Boolean) a.get("correct");
            jdbc.update("""
                    insert into question_attempts(id, user_id, question_id, selected_option_id, correct, context, context_id, created_at)
                    values (?, ?, ?, ?, ?, 'BATTLE', ?, ?)
                    """, Ids.newId(), p.getUserId(), a.get("question_id"), a.get("option_id"), correct, b.getId(), a.get("answered_at"));
            topics.add((UUID) a.get("topic_id"));
            if (correct) {
                xp.award(p.getUserId(), s.zone(), XpService.Source.BATTLE_ANSWER, a.get("bq_id").toString(), XP_PER_CORRECT,
                        "Correct battle answer");
            }
        }
        if (p.getUserId().equals(b.getWinnerId())) {
            xp.award(p.getUserId(), s.zone(), XpService.Source.BATTLE_WIN, b.getId().toString(), XP_WIN, "Won a quiz battle");
        }
        xp.recordActivity(p.getUserId(), s.zone(), 0, 0, answers.size());
        mastery.recompute(p.getUserId(), topics);
        achievements.evaluate(p.getUserId(), s.zone(), s.getDailyMissionMinimum());
    }

    // ------------------------------------------------------------------ recovery & housekeeping

    /** Rebuilds timers for battles that were running when the process stopped. */
    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        for (QuizBattle b : battles.findByStatus(BattleStatus.IN_PROGRESS)) {
            try {
                if (b.getCurrentIndex() < 0) {
                    openRound(b.getId(), 0);
                    continue;
                }
                Timestamp ends = jdbc.queryForObject("select ends_at from quiz_battle_questions where battle_id = ? and position = ?",
                        Timestamp.class, b.getId(), b.getCurrentIndex());
                int index = b.getCurrentIndex();
                if (ends != null && ends.toInstant().isAfter(clock.instant())) {
                    schedule(ends.toInstant(), () -> closeRound(b.getId(), index));
                } else {
                    closeRound(b.getId(), index);
                }
                log.info("Recovered battle {} at round {}", b.getRoomCode(), index);
            } catch (RuntimeException e) {
                log.warn("Could not recover battle {}", b.getId(), e);
            }
        }
    }

    /** Lobbies nobody started within two hours are cancelled. */
    @Scheduled(fixedDelay = 600_000)
    void expireStaleLobbies() {
        Instant cutoff = clock.instant().minus(Duration.ofHours(2));
        jdbc.update("""
                update quiz_battles set status = 'CANCELLED', ended_at = ?, updated_at = ?, version = version + 1
                where status in ('WAITING', 'READY') and created_at < ?
                """, Timestamp.from(clock.instant()), Timestamp.from(clock.instant()), Timestamp.from(cutoff));
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }

    // ------------------------------------------------------------------ reads

    public BattleView view(UUID userId, UUID battleId) {
        requireParticipant(battleId, userId);
        BattleState state = tx.execute(st -> state(battleId));
        UUID mine = null;
        if (state.status() == BattleStatus.IN_PROGRESS && state.live() != null) {
            List<UUID> rows = jdbc.queryForList("""
                    select a.option_id from quiz_answers a join quiz_battle_questions bq on bq.id = a.battle_question_id
                    where bq.battle_id = ? and bq.position = ? and a.user_id = ?
                    """, UUID.class, battleId, state.currentIndex(), userId);
            mine = rows.isEmpty() ? null : rows.getFirst();
        }
        return new BattleView(state, mine);
    }

    public BattleResults results(UUID userId, UUID battleId) {
        requireParticipant(battleId, userId);
        return tx.execute(st -> {
            BattleState state = state(battleId);
            if (state.status() != BattleStatus.COMPLETED) {
                throw ApiException.conflict("NOT_FINISHED", "Results are available when the battle ends");
            }
            List<QuestionBreakdown> out = new ArrayList<>();
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "select id, question_id, position from quiz_battle_questions where battle_id = ? order by position", battleId);
            for (Map<String, Object> r : rows) {
                Question q = questionService.loadOrdered(List.of((UUID) r.get("question_id"))).getFirst();
                out.add(new QuestionBreakdown((Integer) r.get("position"), q.getPrompt(), q.correctOption().getId(),
                        q.correctOption().getBody(), q.getExplanation(), roundResults(battleId, (UUID) r.get("id"))));
            }
            return new BattleResults(state, out);
        });
    }

    public List<HistoryItem> history(UUID userId) {
        return tx.execute(st -> {
            List<HistoryItem> out = new ArrayList<>();
            List<UUID> ids = jdbc.queryForList("""
                    select b.id from quiz_battles b join quiz_battle_participants p on p.battle_id = b.id
                    where p.user_id = ? and b.status in ('COMPLETED', 'CANCELLED')
                    order by coalesce(b.ended_at, b.created_at) desc limit 30
                    """, UUID.class, userId);
            for (UUID id : ids) {
                QuizBattle b = battles.findById(id).orElseThrow();
                List<BattleParticipant> ps = new ArrayList<>(participants.findByBattleIdOrderByJoinedAt(id));
                ps.sort(Comparator.comparingInt(BattleParticipant::getScore).reversed());
                BattleParticipant me = ps.stream().filter(p -> p.getUserId().equals(userId)).findFirst().orElseThrow();
                int placement = 1 + (int) ps.stream().filter(p -> p.getScore() > me.getScore()).count();
                UUID otherId = b.getWinnerId() != null && !b.getWinnerId().equals(userId) ? b.getWinnerId()
                        : ps.stream().map(BattleParticipant::getUserId).filter(u -> !u.equals(userId)).findFirst().orElse(null);
                PublicUser other = otherId == null ? null : userRepo.findById(otherId).map(UserDtos::toPublic).orElse(null);
                out.add(new HistoryItem(b.getId(), b.getRoomCode(), b.getCategory(), b.getDifficulty(), b.getQuestionCount(),
                        b.getStatus(), me.getScore(), me.getCorrectCount(), placement, ps.size(), other,
                        userId.equals(b.getWinnerId()), b.getEndedAt()));
            }
            return out;
        });
    }

    /** Builds the shared state. Must run inside a transaction. */
    private BattleState state(UUID battleId) {
        QuizBattle b = requireBattle(battleId);
        List<BattleParticipant> ps = participants.findByBattleIdOrderByJoinedAt(battleId);
        Map<UUID, PublicUser> people = new HashMap<>();
        userRepo.findAllById(ps.stream().map(BattleParticipant::getUserId).toList())
                .forEach(u -> people.put(u.getId(), UserDtos.toPublic(u)));
        if (!people.containsKey(b.getHostId())) {
            userRepo.findById(b.getHostId()).ifPresent(u -> people.put(u.getId(), UserDtos.toPublic(u)));
        }
        Set<UUID> online = hub.subscribers(topic(battleId));

        Set<UUID> answeredCurrent = new HashSet<>();
        LiveQuestion live = null;
        RoundReveal reveal = null;
        if (b.getCurrentIndex() >= 0 && b.getStatus() != BattleStatus.WAITING) {
            Map<String, Object> bq = jdbc.queryForMap("""
                    select id, question_id, started_at, ends_at from quiz_battle_questions where battle_id = ? and position = ?
                    """, battleId, b.getCurrentIndex());
            UUID bqId = (UUID) bq.get("id");
            answeredCurrent.addAll(jdbc.queryForList("select user_id from quiz_answers where battle_question_id = ?", UUID.class, bqId));
            Question q = questionService.loadOrdered(List.of((UUID) bq.get("question_id"))).getFirst();
            boolean closed = isClosed(battleId, b.getCurrentIndex()) || b.getStatus() == BattleStatus.COMPLETED;
            if (!closed && b.getStatus() == BattleStatus.IN_PROGRESS) {
                live = new LiveQuestion(b.getCurrentIndex(), QuestionService.toView(q, false),
                        ((Timestamp) bq.get("started_at")).toInstant(), ((Timestamp) bq.get("ends_at")).toInstant());
            } else if (closed) {
                reveal = new RoundReveal(b.getCurrentIndex(), q.correctOption().getId(), q.getExplanation(),
                        roundResults(battleId, bqId));
            }
        }
        List<PlayerState> players = ps.stream().map(p -> new PlayerState(people.get(p.getUserId()),
                p.getUserId().equals(b.getHostId()), p.isReady(), p.getScore(), p.getCorrectCount(),
                answeredCurrent.contains(p.getUserId()), online.contains(p.getUserId()))).toList();
        return new BattleState(b.getId(), b.getRoomCode(), b.getStatus(), people.get(b.getHostId()), b.getCategory(),
                b.getCategory().label(), b.getDifficulty(), b.getQuestionCount(), b.getSecondsPerQuestion(),
                b.getCurrentIndex(), players, live, reveal, b.getWinnerId(), b.getStartedAt(), b.getEndedAt());
    }

    private List<PlayerRoundResult> roundResults(UUID battleId, UUID battleQuestionId) {
        Map<UUID, PlayerRoundResult> byUser = new HashMap<>();
        jdbc.query("select user_id, correct, points from quiz_answers where battle_question_id = ?", rs -> {
            UUID u = rs.getObject(1, UUID.class);
            byUser.put(u, new PlayerRoundResult(u, true, rs.getBoolean(2), rs.getInt(3)));
        }, battleQuestionId);
        return participants.findByBattleIdOrderByJoinedAt(battleId).stream()
                .map(p -> byUser.getOrDefault(p.getUserId(), new PlayerRoundResult(p.getUserId(), false, false, 0)))
                .toList();
    }

    // ------------------------------------------------------------------ helpers

    private void broadcast(UUID battleId, String event) {
        BattleState state = tx.execute(st -> state(battleId));
        hub.publish(topic(battleId), event, state);
    }

    private QuizBattle requireBattle(UUID battleId) {
        return battles.findById(battleId).orElseThrow(() -> ApiException.notFound("Battle"));
    }

    private BattleParticipant requireParticipant(UUID battleId, UUID userId) {
        // Non-participants get 404, not 403, so battle ids can't be probed.
        return participants.findByBattleIdAndUserId(battleId, userId).orElseThrow(() -> ApiException.notFound("Battle"));
    }

    private void withLock(UUID battleId, Runnable action) {
        ReentrantLock lock = locks.computeIfAbsent(battleId, k -> new ReentrantLock());
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    private void schedule(Instant at, Runnable task) {
        long delay = Math.max(0, Duration.between(clock.instant(), at).toMillis());
        scheduler.schedule(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                log.error("Battle timer task failed", e);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }
}

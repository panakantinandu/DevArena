package com.devarena.content;

import com.devarena.common.Ids;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Array;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Loads the curated question bank from {@code classpath:content/**}.
 *
 * <p>Versioned and idempotent: each file's SHA-256 is recorded in {@code content_seed_log};
 * unchanged files are skipped, changed files are upserted by slug. The seeder never deletes
 * content, so it is safe to run against production on every start.
 */
@Component
@Order(10)
public class ContentSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ContentSeeder.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;

    public ContentSeeder(JdbcTemplate jdbc, ObjectMapper mapper, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        seedAll();
    }

    public void seedAll() throws IOException {
        // Order matters: later files reference slugs defined by earlier ones.
        apply("content/topics.json", this::seedTopics);
        for (Resource r : resources("classpath:content/questions/*.json")) {
            apply("content/questions/" + r.getFilename(), this::seedQuestions);
        }
        apply("content/coding-problems.json", this::seedCodingProblems);
        apply("content/design-problems.json", this::seedDesignProblems);
        for (Resource r : resources("classpath:content/interview/*.json")) {
            apply("content/interview/" + r.getFilename(), this::seedInterviewQuestions);
        }
        apply("content/achievements.json", this::seedAchievements);
        for (Resource r : resources("classpath:content/gym/*.json")) {
            apply("content/gym/" + r.getFilename(), this::seedGymPuzzles);
        }
    }

    private interface Loader { void load(JsonNode root); }

    private void apply(String path, Loader loader) throws IOException {
        Resource res = new PathMatchingResourcePatternResolver().getResource("classpath:" + path);
        if (!res.exists()) return;
        byte[] bytes;
        try (InputStream in = res.getInputStream()) {
            bytes = in.readAllBytes();
        }
        String checksum = sha256(bytes);
        List<String> existing = jdbc.queryForList("select checksum from content_seed_log where name = ?", String.class, path);
        if (!existing.isEmpty() && existing.getFirst().equals(checksum)) return;

        JsonNode root = mapper.readTree(new String(bytes, StandardCharsets.UTF_8));
        tx.executeWithoutResult(status -> {
            loader.load(root);
            jdbc.update("""
                    insert into content_seed_log(name, checksum, applied_at) values (?, ?, ?)
                    on conflict (name) do update set checksum = excluded.checksum, applied_at = excluded.applied_at
                    """, path, checksum, Timestamp.from(Instant.now()));
        });
        log.info("Seeded content file {}", path);
    }

    // ------------------------------------------------------------------ loaders

    private void seedTopics(JsonNode root) {
        int order = 0;
        for (JsonNode t : root) {
            jdbc.update("""
                    insert into topics(id, slug, name, category, description, sort_order) values (?, ?, ?, ?, ?, ?)
                    on conflict (slug) do update set name = excluded.name, category = excluded.category,
                        description = excluded.description, sort_order = excluded.sort_order
                    """, Ids.newId(), t.get("slug").asString(), t.get("name").asString(), t.get("category").asString(),
                    text(t, "description"), order++);
        }
    }

    /** A question file is an array of {topic, questions[]} groups. */
    private void seedQuestions(JsonNode root) {
        for (JsonNode group : root) {
            seedQuestionGroup(topicId(group.get("topic").asString()), group.get("questions"));
        }
    }

    private void seedQuestionGroup(UUID topicId, JsonNode questions) {
        for (JsonNode q : questions) {
            String slug = q.get("slug").asString();
            JsonNode rawOptions = q.get("options");
            int rawAnswer = q.get("answer").asInt();
            if (rawOptions.size() < 2 || rawAnswer < 0 || rawAnswer >= rawOptions.size()) {
                throw new IllegalStateException("Invalid options for question " + slug);
            }
            // Authors tend to put the right answer in the same slot. Shuffle with a seed derived
            // from the slug so the order is stable across re-seeds but not guessable.
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < rawOptions.size(); i++) order.add(i);
            java.util.Collections.shuffle(order, new java.util.Random(slug.hashCode()));
            List<String> options = order.stream().map(i -> rawOptions.get(i).asString()).toList();
            int answer = order.indexOf(rawAnswer);
            Instant now = Instant.now();
            UUID questionId = jdbc.queryForObject("""
                    insert into questions(id, slug, topic_id, difficulty, prompt, code_snippet, explanation, status, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
                    on conflict (slug) do update set topic_id = excluded.topic_id, difficulty = excluded.difficulty,
                        prompt = excluded.prompt, code_snippet = excluded.code_snippet, explanation = excluded.explanation,
                        updated_at = excluded.updated_at, version = questions.version + 1
                    returning id
                    """, UUID.class, Ids.newId(), slug, topicId, q.get("difficulty").asString(), q.get("prompt").asString(),
                    text(q, "code"), q.get("explanation").asString(), Timestamp.from(now), Timestamp.from(now));

            // Clear the correct flag first so the "one correct option" index never sees two.
            jdbc.update("update question_options set correct = false where question_id = ?", questionId);
            for (int i = 0; i < options.size(); i++) {
                int updated = jdbc.update("update question_options set body = ? where question_id = ? and position = ?",
                        options.get(i), questionId, i);
                if (updated == 0) {
                    jdbc.update("insert into question_options(id, question_id, position, body, correct) values (?, ?, ?, ?, false)",
                            Ids.newId(), questionId, i, options.get(i));
                }
            }
            jdbc.update("update question_options set correct = true where question_id = ? and position = ?", questionId, answer);
            // Drop options that no longer exist, unless someone already answered with them.
            jdbc.update("""
                    delete from question_options o where o.question_id = ? and o.position >= ?
                      and not exists (select 1 from question_attempts a where a.selected_option_id = o.id)
                      and not exists (select 1 from quiz_answers qa where qa.option_id = o.id)
                    """, questionId, options.size());
        }
    }

    private void seedCodingProblems(JsonNode root) {
        for (JsonNode p : root) {
            Instant now = Instant.now();
            jdbc.update("""
                    insert into coding_problems(id, slug, title, topic_id, difficulty, statement, examples, constraints_text,
                        starter_code, solution_outline, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    on conflict (slug) do update set title = excluded.title, topic_id = excluded.topic_id,
                        difficulty = excluded.difficulty, statement = excluded.statement, examples = excluded.examples,
                        constraints_text = excluded.constraints_text, starter_code = excluded.starter_code,
                        solution_outline = excluded.solution_outline, updated_at = excluded.updated_at
                    """, Ids.newId(), p.get("slug").asString(), p.get("title").asString(), topicId(p.get("topic").asString()),
                    p.get("difficulty").asString(), p.get("statement").asString(), p.get("examples").asString(),
                    p.get("constraints").asString(), p.get("starterCode").asString(), p.get("solutionOutline").asString(),
                    Timestamp.from(now), Timestamp.from(now));
        }
    }

    private void seedDesignProblems(JsonNode root) {
        for (JsonNode p : root) {
            Instant now = Instant.now();
            jdbc.update(con -> {
                var ps = con.prepareStatement("""
                        insert into design_problems(id, slug, title, difficulty, summary, functional_reqs, non_functional_reqs,
                            discussion_topics, follow_ups, created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        on conflict (slug) do update set title = excluded.title, difficulty = excluded.difficulty,
                            summary = excluded.summary, functional_reqs = excluded.functional_reqs,
                            non_functional_reqs = excluded.non_functional_reqs, discussion_topics = excluded.discussion_topics,
                            follow_ups = excluded.follow_ups, updated_at = excluded.updated_at
                        """);
                ps.setObject(1, Ids.newId());
                ps.setString(2, p.get("slug").asString());
                ps.setString(3, p.get("title").asString());
                ps.setString(4, p.get("difficulty").asString());
                ps.setString(5, p.get("summary").asString());
                ps.setArray(6, array(con, p.get("functional")));
                ps.setArray(7, array(con, p.get("nonFunctional")));
                ps.setArray(8, array(con, p.get("discussionTopics")));
                ps.setArray(9, array(con, p.get("followUps")));
                ps.setTimestamp(10, Timestamp.from(now));
                ps.setTimestamp(11, Timestamp.from(now));
                return ps;
            });
        }
    }

    private void seedInterviewQuestions(JsonNode root) {
        String type = root.get("type").asString();
        for (JsonNode q : root.get("questions")) {
            UUID coding = q.has("codingProblem") ? idBySlug("coding_problems", q.get("codingProblem").asString()) : null;
            UUID design = q.has("designProblem") ? idBySlug("design_problems", q.get("designProblem").asString()) : null;
            Instant now = Instant.now();
            jdbc.update(con -> {
                var ps = con.prepareStatement("""
                        insert into interview_questions(id, slug, interview_type, difficulty, title, prompt, coding_problem_id,
                            design_problem_id, expected_concepts, answer_outline, common_mistakes, follow_ups, rubric_notes,
                            created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        on conflict (slug) do update set interview_type = excluded.interview_type,
                            difficulty = excluded.difficulty, title = excluded.title, prompt = excluded.prompt,
                            coding_problem_id = excluded.coding_problem_id, design_problem_id = excluded.design_problem_id,
                            expected_concepts = excluded.expected_concepts, answer_outline = excluded.answer_outline,
                            common_mistakes = excluded.common_mistakes, follow_ups = excluded.follow_ups,
                            rubric_notes = excluded.rubric_notes, updated_at = excluded.updated_at
                        """);
                ps.setObject(1, Ids.newId());
                ps.setString(2, q.get("slug").asString());
                ps.setString(3, type);
                ps.setString(4, q.get("difficulty").asString());
                ps.setString(5, q.get("title").asString());
                ps.setString(6, q.get("prompt").asString());
                ps.setObject(7, coding);
                ps.setObject(8, design);
                ps.setArray(9, array(con, q.get("expectedConcepts")));
                ps.setString(10, q.get("answerOutline").asString());
                ps.setArray(11, array(con, q.get("commonMistakes")));
                ps.setArray(12, array(con, q.get("followUps")));
                ps.setString(13, q.get("rubricNotes").asString());
                ps.setTimestamp(14, Timestamp.from(now));
                ps.setTimestamp(15, Timestamp.from(now));
                return ps;
            });
        }
    }

    private void seedAchievements(JsonNode root) {
        for (JsonNode a : root) {
            jdbc.update("""
                    insert into achievements(id, code, name, description, icon, criteria_type, threshold)
                    values (?, ?, ?, ?, ?, ?, ?)
                    on conflict (code) do update set name = excluded.name, description = excluded.description,
                        icon = excluded.icon, criteria_type = excluded.criteria_type, threshold = excluded.threshold
                    """, Ids.newId(), a.get("code").asString(), a.get("name").asString(), a.get("description").asString(),
                    a.get("icon").asString(), a.get("criteria").asString(), a.get("threshold").asInt());
        }
    }

    /** Brain Gym puzzles: public payload and private secret stored as separate JSONB columns. */
    private void seedGymPuzzles(JsonNode root) {
        for (JsonNode p : root) {
            Instant now = Instant.now();
            jdbc.update("""
                    insert into gym_puzzles(id, slug, mode, difficulty, title, payload, secret, explanation, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?)
                    on conflict (slug) do update set mode = excluded.mode, difficulty = excluded.difficulty, title = excluded.title,
                        payload = excluded.payload, secret = excluded.secret, explanation = excluded.explanation,
                        updated_at = excluded.updated_at
                    """, Ids.newId(), p.get("slug").asString(), p.get("mode").asString(), p.get("difficulty").asString(),
                    p.get("title").asString(), mapper.writeValueAsString(p.get("public")), mapper.writeValueAsString(p.get("secret")),
                    p.get("explanation").asString(), Timestamp.from(now), Timestamp.from(now));
        }
    }

    // ------------------------------------------------------------------ helpers

    private UUID topicId(String slug) {
        return idBySlug("topics", slug);
    }

    private UUID idBySlug(String table, String slug) {
        List<UUID> ids = jdbc.queryForList("select id from " + table + " where slug = ?", UUID.class, slug);
        if (ids.isEmpty()) throw new IllegalStateException("Seed references unknown " + table + " slug: " + slug);
        return ids.getFirst();
    }

    private static Array array(java.sql.Connection con, JsonNode node) throws java.sql.SQLException {
        List<String> values = new ArrayList<>();
        if (node != null) node.forEach(n -> values.add(n.asString()));
        return con.createArrayOf("text", values.toArray());
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asString();
    }

    private static List<Resource> resources(String pattern) throws IOException {
        Resource[] found = new PathMatchingResourcePatternResolver().getResources(pattern);
        return Arrays.stream(found).sorted(Comparator.comparing(Resource::getFilename)).toList();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

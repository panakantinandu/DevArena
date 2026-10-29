package com.devarena;

import static org.assertj.core.api.Assertions.assertThat;

import com.devarena.content.ContentSeeder;
import com.devarena.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ApplicationBootTest extends IntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ContentSeeder seeder;

    @Test
    void migratesAndSeedsContent() {
        assertThat(count("select count(*) from topics")).isGreaterThan(40);
        assertThat(count("select count(*) from questions")).isGreaterThan(100);
        assertThat(count("select count(*) from interview_questions")).isGreaterThan(20);
        assertThat(count("select count(*) from design_problems")).isEqualTo(6);
        // Every question has exactly one correct option (also enforced by a partial unique index).
        assertThat(count("""
                select count(*) from questions q
                where (select count(*) from question_options o where o.question_id = q.id and o.correct) <> 1
                """)).isZero();
    }

    @Test
    void seedingIsIdempotent() throws Exception {
        long before = count("select count(*) from questions");
        jdbc.update("delete from content_seed_log"); // force a full re-apply
        seeder.seedAll();
        assertThat(count("select count(*) from questions")).isEqualTo(before);
        assertThat(count("select count(*) from question_options where correct")).isEqualTo(before);
    }

    @Test
    void correctAnswersAreNotAllInOnePosition() {
        // The seeder shuffles options; no single slot should hold most correct answers.
        long maxInOneSlot = count("""
                select max(c) from (select position, count(*) c from question_options where correct group by position) x
                """);
        long total = count("select count(*) from questions");
        assertThat(maxInOneSlot).isLessThan(total / 2);
    }

    private long count(String sql) {
        Long v = jdbc.queryForObject(sql, Long.class);
        return v == null ? 0 : v;
    }
}

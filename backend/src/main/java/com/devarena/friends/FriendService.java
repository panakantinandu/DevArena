package com.devarena.friends;

import com.devarena.common.AfterCommit;
import com.devarena.common.ApiException;
import com.devarena.common.Ids;
import com.devarena.gamification.LevelService;
import com.devarena.gamification.StreakService;
import com.devarena.gamification.XpService;
import com.devarena.realtime.RealtimeHub;
import com.devarena.users.User;
import com.devarena.users.UserDtos;
import com.devarena.users.UserDtos.PublicProfile;
import com.devarena.users.UserDtos.PublicUser;
import com.devarena.users.UserService;
import com.devarena.users.UserSettings;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FriendService {
    public record Friend(UUID friendshipId, PublicUser user, Instant since) {}

    public record FriendRequest(UUID friendshipId, PublicUser user, Instant sentAt) {}

    public record FriendsOverview(List<Friend> friends, List<FriendRequest> incoming, List<FriendRequest> outgoing) {}

    private final JdbcTemplate jdbc;
    private final UserService users;
    private final RealtimeHub hub;
    private final XpService xp;
    private final LevelService levels;
    private final StreakService streaks;
    private final Clock clock;

    public FriendService(JdbcTemplate jdbc, UserService users, RealtimeHub hub, XpService xp, LevelService levels,
                         StreakService streaks, Clock clock) {
        this.jdbc = jdbc;
        this.users = users;
        this.hub = hub;
        this.xp = xp;
        this.levels = levels;
        this.streaks = streaks;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FriendsOverview overview(UUID me) {
        List<Friend> friends = jdbc.query("""
                select f.id, u.id, u.username, u.display_name, u.avatar_url, f.updated_at
                from friendships f join users u on u.id = case when f.requester_id = ? then f.addressee_id else f.requester_id end
                where f.status = 'ACCEPTED' and (f.requester_id = ? or f.addressee_id = ?)
                order by u.display_name
                """, (rs, i) -> new Friend(rs.getObject(1, UUID.class), user(rs), rs.getTimestamp(6).toInstant()), me, me, me);
        List<FriendRequest> incoming = jdbc.query("""
                select f.id, u.id, u.username, u.display_name, u.avatar_url, f.created_at
                from friendships f join users u on u.id = f.requester_id
                where f.status = 'PENDING' and f.addressee_id = ? order by f.created_at desc
                """, (rs, i) -> new FriendRequest(rs.getObject(1, UUID.class), user(rs), rs.getTimestamp(6).toInstant()), me);
        List<FriendRequest> outgoing = jdbc.query("""
                select f.id, u.id, u.username, u.display_name, u.avatar_url, f.created_at
                from friendships f join users u on u.id = f.addressee_id
                where f.status = 'PENDING' and f.requester_id = ? order by f.created_at desc
                """, (rs, i) -> new FriendRequest(rs.getObject(1, UUID.class), user(rs), rs.getTimestamp(6).toInstant()), me);
        return new FriendsOverview(friends, incoming, outgoing);
    }

    private static PublicUser user(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PublicUser(rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getString(5));
    }

    @Transactional
    public void request(UUID me, UUID target) {
        if (me.equals(target)) throw ApiException.badRequest("SELF", "You can't befriend yourself");
        users.require(target);
        List<Map<String, Object>> existing = pair(me, target);
        if (!existing.isEmpty()) {
            Map<String, Object> f = existing.getFirst();
            if ("PENDING".equals(f.get("status")) && target.equals(f.get("requester_id"))) {
                // They already asked you: sending a request back accepts it.
                accept(me, (UUID) f.get("id"));
                return;
            }
            throw ApiException.conflict("ALREADY_EXISTS", "PENDING".equals(f.get("status"))
                    ? "Request already sent" : "You're already friends");
        }
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("insert into friendships(id, requester_id, addressee_id, status, created_at, updated_at) values (?, ?, ?, 'PENDING', ?, ?)",
                Ids.newId(), me, target, now, now);
        AfterCommit.run(() -> hub.sendToUser(target, "FRIEND_REQUEST", Map.of("from", UserDtos.toPublic(users.require(me)))));
    }

    @Transactional
    public void accept(UUID me, UUID friendshipId) {
        int n = jdbc.update("""
                update friendships set status = 'ACCEPTED', updated_at = ?
                where id = ? and addressee_id = ? and status = 'PENDING'
                """, Timestamp.from(clock.instant()), friendshipId, me);
        if (n == 0) throw ApiException.notFound("Friend request");
        UUID requester = jdbc.queryForObject("select requester_id from friendships where id = ?", UUID.class, friendshipId);
        AfterCommit.run(() -> hub.sendToUser(requester, "FRIEND_ACCEPTED", Map.of("by", UserDtos.toPublic(users.require(me)))));
    }

    /** Rejects an incoming request or withdraws an outgoing one. */
    @Transactional
    public void decline(UUID me, UUID friendshipId) {
        int n = jdbc.update("delete from friendships where id = ? and status = 'PENDING' and (addressee_id = ? or requester_id = ?)",
                friendshipId, me, me);
        if (n == 0) throw ApiException.notFound("Friend request");
    }

    @Transactional
    public void remove(UUID me, UUID friendUserId) {
        int n = jdbc.update("""
                delete from friendships where status = 'ACCEPTED'
                  and ((requester_id = ? and addressee_id = ?) or (requester_id = ? and addressee_id = ?))
                """, me, friendUserId, friendUserId, me);
        if (n == 0) throw ApiException.notFound("Friend");
    }

    public boolean areFriends(UUID a, UUID b) {
        List<Map<String, Object>> p = pair(a, b);
        return !p.isEmpty() && "ACCEPTED".equals(p.getFirst().get("status"));
    }

    @Transactional(readOnly = true)
    public PublicProfile profile(UUID me, UUID userId) {
        User u = users.require(userId);
        UserSettings s = users.settings(userId);
        long total = xp.totalXp(userId);
        var level = levels.forXp(total);
        int streak = streaks.streak(userId, s.zone(), s.getDailyMissionMinimum()).current();
        String relation;
        if (me.equals(userId)) {
            relation = "SELF";
        } else {
            List<Map<String, Object>> p = pair(me, userId);
            if (p.isEmpty()) relation = "NONE";
            else if ("ACCEPTED".equals(p.getFirst().get("status"))) relation = "FRIENDS";
            else relation = me.equals(p.getFirst().get("requester_id")) ? "REQUEST_SENT" : "REQUEST_RECEIVED";
        }
        return new PublicProfile(u.getId(), u.getUsername(), u.getDisplayName(), u.getAvatarUrl(), u.getBio(),
                s.getTargetRole(), total, level.level(), level.title(), streak, relation);
    }

    private List<Map<String, Object>> pair(UUID a, UUID b) {
        return jdbc.queryForList("""
                select id, requester_id, addressee_id, status from friendships
                where (requester_id = ? and addressee_id = ?) or (requester_id = ? and addressee_id = ?)
                """, a, b, b, a);
    }
}

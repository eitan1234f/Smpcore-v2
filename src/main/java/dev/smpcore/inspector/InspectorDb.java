package dev.smpcore.inspector;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite storage for the inspector. All database work happens on one background thread.
 */
public final class InspectorDb {
    private final Logger logger;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SmpCore-Inspector");
        t.setDaemon(true);
        return t;
    });
    private final ConcurrentLinkedQueue<Entry> pending = new ConcurrentLinkedQueue<>();
    private Connection connection;

    /** One logged change. kind 0 = block change (old -> new), kind 1 = container item (action 0 removed / 1 added). */
    public record Entry(long id, long time, String user, String world, int x, int y, int z, int kind, int action,
                        String oldData, String newData, String material, int amount, byte[] item, boolean rolledBack) {
    }

    public static final int BREAK = 0, PLACE = 1, EXPLODE = 2, BURN = 3, BUCKET = 4;
    public static final int REMOVED = 0, ADDED = 1;

    public InspectorDb(Logger logger) {
        this.logger = logger;
    }

    public boolean open(File file) {
        try {
            Class.forName("org.sqlite.JDBC");
            file.getParentFile().mkdirs();
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            try (Statement s = connection.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA synchronous=NORMAL");
                s.execute("CREATE TABLE IF NOT EXISTS log (id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, user TEXT NOT NULL, "
                        + "world TEXT NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL, kind INTEGER NOT NULL, "
                        + "action INTEGER NOT NULL, old_data TEXT, new_data TEXT, material TEXT, amount INTEGER, item BLOB, rolled INTEGER DEFAULT 0)");
                s.execute("CREATE INDEX IF NOT EXISTS log_pos ON log (world, x, z, y)");
                s.execute("CREATE INDEX IF NOT EXISTS log_user ON log (user COLLATE NOCASE, time)");
                s.execute("CREATE INDEX IF NOT EXISTS log_time ON log (time)");
            }
            return true;
        } catch (ClassNotFoundException | SQLException e) {
            logger.log(Level.SEVERE, "Inspector database could not be opened - block logging is disabled", e);
            connection = null;
            return false;
        }
    }

    public boolean isOpen() {
        return connection != null;
    }

    public void queue(Entry e) {
        if (connection != null) pending.add(e);
    }

    public void flush() {
        if (connection == null || pending.isEmpty()) return;
        executor.execute(this::flushNow);
    }

    private void flushNow() {
        if (pending.isEmpty()) return;
        String sql = "INSERT INTO log (time, user, world, x, y, z, kind, action, old_data, new_data, material, amount, item) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            Entry e;
            int n = 0;
            while ((e = pending.poll()) != null) {
                ps.setLong(1, e.time());
                ps.setString(2, e.user());
                ps.setString(3, e.world());
                ps.setInt(4, e.x());
                ps.setInt(5, e.y());
                ps.setInt(6, e.z());
                ps.setInt(7, e.kind());
                ps.setInt(8, e.action());
                ps.setString(9, e.oldData());
                ps.setString(10, e.newData());
                ps.setString(11, e.material());
                ps.setInt(12, e.amount());
                ps.setBytes(13, e.item());
                ps.addBatch();
                if (++n % 500 == 0) ps.executeBatch();
            }
            ps.executeBatch();
            connection.commit();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Inspector write failed", ex);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
                // connection closing
            }
        }
    }

    /** Filters for lookups and rollbacks. Null fields are ignored. */
    public static final class Query {
        public String user;
        public Long since;
        public String world;
        public Integer cx, cy, cz, radius;
        public Integer x, y, z;
        public Integer kind;
        public List<Integer> actions;
        public List<String> materials;
        public Boolean rolled;
        public boolean ascending;
        public int limit = 1000;
        public int offset;
    }

    public CompletableFuture<List<Entry>> lookup(Query q) {
        return CompletableFuture.supplyAsync(() -> {
            flushNow();
            List<Object> params = new ArrayList<>();
            StringBuilder sql = new StringBuilder("SELECT * FROM log WHERE 1=1");
            if (q.user != null) { sql.append(" AND user = ? COLLATE NOCASE"); params.add(q.user); }
            if (q.since != null) { sql.append(" AND time >= ?"); params.add(q.since); }
            if (q.world != null) { sql.append(" AND world = ?"); params.add(q.world); }
            if (q.x != null) {
                sql.append(" AND x = ? AND y = ? AND z = ?");
                params.add(q.x);
                params.add(q.y);
                params.add(q.z);
            }
            if (q.radius != null && q.cx != null) {
                sql.append(" AND x BETWEEN ? AND ? AND z BETWEEN ? AND ? AND y BETWEEN ? AND ?");
                params.add(q.cx - q.radius);
                params.add(q.cx + q.radius);
                params.add(q.cz - q.radius);
                params.add(q.cz + q.radius);
                params.add(q.cy - q.radius);
                params.add(q.cy + q.radius);
            }
            if (q.kind != null) { sql.append(" AND kind = ?"); params.add(q.kind); }
            if (q.actions != null && !q.actions.isEmpty()) {
                sql.append(" AND action IN (").append("?,".repeat(q.actions.size() - 1)).append("?)");
                params.addAll(q.actions);
            }
            if (q.materials != null && !q.materials.isEmpty()) {
                sql.append(" AND (");
                for (int i = 0; i < q.materials.size(); i++) {
                    if (i > 0) sql.append(" OR ");
                    String m = q.materials.get(i);
                    sql.append("old_data = ? OR old_data LIKE ? OR new_data = ? OR new_data LIKE ? OR material = ?");
                    params.add(m);
                    params.add(m + "[%");
                    params.add(m);
                    params.add(m + "[%");
                    params.add(m);
                }
                sql.append(")");
            }
            if (q.rolled != null) { sql.append(" AND rolled = ?"); params.add(q.rolled ? 1 : 0); }
            sql.append(" ORDER BY time ").append(q.ascending ? "ASC" : "DESC").append(", id ").append(q.ascending ? "ASC" : "DESC");
            sql.append(" LIMIT ? OFFSET ?");
            params.add(q.limit);
            params.add(q.offset);
            List<Entry> out = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new Entry(rs.getLong("id"), rs.getLong("time"), rs.getString("user"), rs.getString("world"),
                                rs.getInt("x"), rs.getInt("y"), rs.getInt("z"), rs.getInt("kind"), rs.getInt("action"),
                                rs.getString("old_data"), rs.getString("new_data"), rs.getString("material"), rs.getInt("amount"),
                                rs.getBytes("item"), rs.getInt("rolled") == 1));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
            return out;
        }, executor);
    }

    public CompletableFuture<Void> markRolled(List<Long> ids, boolean rolled) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement("UPDATE log SET rolled = ? WHERE id = ?")) {
                connection.setAutoCommit(false);
                for (long id : ids) {
                    ps.setInt(1, rolled ? 1 : 0);
                    ps.setLong(2, id);
                    ps.addBatch();
                }
                ps.executeBatch();
                connection.commit();
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Inspector update failed", e);
            }
        }, executor);
    }

    public CompletableFuture<Integer> purge(long olderThan) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM log WHERE time < ?")) {
                ps.setLong(1, olderThan);
                return ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Inspector purge failed", e);
                return 0;
            }
        }, executor);
    }

    public void close() {
        if (connection == null) return;
        executor.execute(this::flushNow);
        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
            connection.close();
        } catch (InterruptedException | SQLException ignored) {
            Thread.currentThread().interrupt();
        }
        connection = null;
    }
}

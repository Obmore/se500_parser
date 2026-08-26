package se500;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

final class Store implements AutoCloseable {

    private final Connection conn;
    private final Map<String, Integer> machines = new HashMap<>();
    private final Map<String, Integer> products = new HashMap<>();
    private final Map<String, Integer> features = new HashMap<>();

    Store(Properties jdbc) throws SQLException {
        conn = DriverManager.getConnection(
                jdbc.getProperty("jdbc.url"),
                jdbc.getProperty("jdbc.user"),
                jdbc.getProperty("jdbc.password"));
        conn.setAutoCommit(false);
    }

    boolean alreadyLoaded(String sourceFile) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM inspection WHERE source_file = ?")) {
            ps.setString(1, sourceFile);
            try (ResultSet rs = ps.executeQuery()) {
                boolean found = rs.next();
                conn.commit();
                return found;
            }
        }
    }

    void save(Inspection insp) throws SQLException {
        int machineId = upsert(
                "INSERT INTO machine (system_id) VALUES (?) ON CONFLICT (system_id) DO UPDATE SET system_id = EXCLUDED.system_id RETURNING id",
                machines,
                insp.systemId);
        int productId = upsert(
                "INSERT INTO product (name) VALUES (?) ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name RETURNING id",
                products,
                insp.panelName);

        long inspectionId;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO inspection (machine_id, product_id, serial_code, test_time, status, source_file) "
                        + "VALUES (?,?,?,?,?,?) RETURNING id")) {
            ps.setInt(1, machineId);
            ps.setInt(2, productId);
            ps.setString(3, insp.serialCode);
            ps.setTimestamp(4, Timestamp.valueOf(insp.testTime));
            ps.setString(5, String.valueOf(insp.status));
            ps.setString(6, insp.sourceFile);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                inspectionId = rs.getLong(1);
            }
        }

        try (PreparedStatement def = conn.prepareStatement(
                """
                INSERT INTO feature_def (
                    product_id, image_name, location_name, feature_name,
                    h_up_fail, h_up_warn, h_target, h_low_warn, h_low_fail,
                    a_up_fail, a_up_warn, a_target, a_low_warn, a_low_fail,
                    v_up_fail, v_up_warn, v_target, v_low_warn, v_low_fail
                ) VALUES (?,?,?,?, ?,?,?,?,?, ?,?,?,?,?, ?,?,?,?,?)
                ON CONFLICT (product_id, image_name, location_name, feature_name)
                DO UPDATE SET feature_name = EXCLUDED.feature_name
                RETURNING id
                """);
             PreparedStatement meas = conn.prepareStatement(
                     "INSERT INTO measurement (inspection_id, feature_def_id, height, area, volume) VALUES (?,?,?,?,?)")) {

            int n = 0;
            for (Measurement m : insp.measurements) {
                String key = productId + "|" + m.imageName + "|" + m.locationName + "|" + m.featureName;
                Integer defId = features.get(key);
                if (defId == null) {
                    def.setInt(1, productId);
                    def.setString(2, m.imageName);
                    def.setString(3, m.locationName);
                    def.setString(4, m.featureName);
                    bindLimit(def, 5, m.hUpFail);
                    bindLimit(def, 6, m.hUpWarn);
                    bindLimit(def, 7, m.hTarget);
                    bindLimit(def, 8, m.hLowWarn);
                    bindLimit(def, 9, m.hLowFail);
                    bindLimit(def, 10, m.aUpFail);
                    bindLimit(def, 11, m.aUpWarn);
                    bindLimit(def, 12, m.aTarget);
                    bindLimit(def, 13, m.aLowWarn);
                    bindLimit(def, 14, m.aLowFail);
                    bindLimit(def, 15, m.vUpFail);
                    bindLimit(def, 16, m.vUpWarn);
                    bindLimit(def, 17, m.vTarget);
                    bindLimit(def, 18, m.vLowWarn);
                    bindLimit(def, 19, m.vLowFail);
                    try (ResultSet rs = def.executeQuery()) {
                        rs.next();
                        defId = rs.getInt(1);
                    }
                    features.put(key, defId);
                }

                meas.setLong(1, inspectionId);
                meas.setInt(2, defId);
                meas.setFloat(3, m.height);
                meas.setFloat(4, m.area);
                meas.setFloat(5, m.volume);
                meas.addBatch();
                if (++n % 400 == 0) {
                    meas.executeBatch();
                }
            }
            meas.executeBatch();
        }

        conn.commit();
    }

    void rollbackQuietly() {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
        }
    }

    @Override
    public void close() throws SQLException {
        conn.close();
    }

    private int upsert(String sql, Map<String, Integer> cache, String key) throws SQLException {
        Integer id = cache.get(key);
        if (id != null) {
            return id;
        }
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                id = rs.getInt(1);
            }
        }
        cache.put(key, id);
        return id;
    }

    private static void bindLimit(PreparedStatement ps, int idx, float v) throws SQLException {
        if (Float.isNaN(v)) {
            ps.setNull(idx, Types.REAL);
        } else {
            ps.setFloat(idx, v);
        }
    }
}

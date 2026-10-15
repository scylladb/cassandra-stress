package org.apache.cassandra.stress.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StatementIdempotenceTest {
    @ParameterizedTest
    @ValueSource(
            strings = {
                "SELECT * FROM standard1 WHERE KEY=?",
                "  select v from t where k = ?",
                "INSERT INTO standard1 (KEY,\"C0\") VALUES (?,?)",
                "UPDATE t SET v = ? WHERE k = ?",
                "UPDATE t SET total = ?, totals = ? WHERE k = ?",
                "UPDATE t SET v = 'ref' WHERE k = ?",
                "DELETE FROM t WHERE k = ?",
                "INSERT INTO t (k, v) VALUES (0x0102, -5)",
                "SELECT * FROM t WHERE token(k) > ? AND token(k) <= ?",
                "UPDATE t SET a = ?, b = c + ? WHERE k = ?",
                "DELETE FROM t WHERE k = ? AND c IN [1, 2]",
                "SELECT * FROM t WHERE k = ? AND ts > maxTimeuuid(?)",
                "INSERT INTO t (k, nowhere) VALUES (?, ?)",
                "SELECT * FROM t WHERE token(k) >= ? AND token(k) <= ?",
                "INSERT INTO t (k, v) VALUES (?, 'a += b')",
                "INSERT INTO t (k, v) VALUES (?, 'if uuid() then l = l + 1')",
                "INSERT INTO t (k, v) VALUES (?, 'it''s IF')",
                "UPDATE \"a\"\"b\" SET \"x\"\"y\" = ? WHERE k = ?",
                "UPDATE t SET v = ? WHERE k = ? -- it's c = c + 1",
                "UPDATE t SET v = $$c = c + 1 IF$$ WHERE k = ?",
                "SELECT JSON * FROM t WHERE k = ?",
                "INSERT INTO t (k, \"it's\") VALUES (?, ?)",
                "SELECT k, TTL(v) FROM t WHERE k = ?",
                "INSERT INTO t (k, v) VALUES (?, ?) USING TIMESTAMP ?",
                "DELETE FROM t USING TIMESTAMP ? WHERE k = ?",
            })
    void plainDmlIsIdempotent(String cql) {
        assertTrue(StatementIdempotence.isIdempotent(cql), cql);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "UPDATE counter1 SET \"C0\"=\"C0\"+?,\"C1\"=\"C1\"+? WHERE KEY=?",
                "UPDATE counter1 SET \"C0\"=\"C0\"+5 WHERE KEY=0x01",
                "update t set c = c - ? where k = ?",
                "UPDATE t SET c = C + ? WHERE k = ?",
                "UPDATE t SET \"x\"\"y\" = \"x\"\"y\" + ? WHERE k = ?",
                "UPDATE t SET l = l + ? WHERE k = ?",
                "UPDATE t SET a = ?, l = l + ? WHERE k = ?",
                "UPDATE t SET l = ? + l WHERE k = ?",
                "UPDATE t SET l = [1, 2] + l WHERE k = ?",
                "INSERT INTO t (k, v) VALUES (?, ?) IF NOT EXISTS",
                "UPDATE t SET v = ? WHERE k = ? IF v = ?",
                "DELETE FROM t WHERE k = ? IF EXISTS",
                "update t set v = ? where k = ? if exists",
                "CREATE TABLE IF NOT EXISTS t (k int PRIMARY KEY)",
                "TRUNCATE t",
                "USE ks",
                "BEGIN BATCH INSERT INTO t (k) VALUES (1); APPLY BATCH",
                "INSERT INTO t (k, id) VALUES (?, uuid())",
                "INSERT INTO t (k, at) VALUES (?, now())",
                "UPDATE t SET at = toTimestamp(NOW ()) WHERE k = ?",
                "INSERT INTO t (k, id) VALUES (?, currentTimeUUID())",
                "INSERT INTO t (k, at) VALUES (?, currentTimestamp())",
                "INSERT INTO t (k, d) VALUES (?, currentDate())",
                "INSERT INTO t (k, d) VALUES (?, currentTime())",
                "DELETE l[0] FROM t WHERE k = ?",
                "UPDATE t SET l = [1, 2] WHERE k = ?",
                "INSERT INTO t (k, l) VALUES (?, [1, 2])",
                "UPDATE t SET l[0] = ? WHERE k = ?",
                "UPDATE t SET views += ? WHERE k = ?",
                "UPDATE t SET c -= 1 WHERE k = ?",
                "UPDATE t SET l += [1] WHERE k = ?",
                "delete m['a'], l[?] from t where k = ?",
                "UPDATE t SET \"c\" = c + 1 WHERE k = ?",
                "UPDATE t SET c = \"c\" + 1 WHERE k = ?",
                "UPDATE t SET l = ? + \"l\" WHERE k = ?",
                "INSERT INTO t JSON ?",
                "INSERT INTO t JSON '{\"k\": 1, \"l\": [1]}'",
                "UPDATE t SET l = fromJson(?) WHERE k = ?",
                "UPDATE t /* it's */ SET c = c + 1 WHERE k = ?",
                "UPDATE t SET v = $$it's$$, c = c + 1 WHERE k = ?",
                "UPDATE t SET v = ? // it's\n, c = c + 1 WHERE k = ?",
                "INSERT INTO t (k, v) VALUES (?, ?) USING TTL ?",
                "INSERT INTO t (k, v) VALUES (?, ?) USING TTL 86400",
                "insert into t (k, v) values (?, ?) using timestamp ? and ttl ?",
                "UPDATE t USING TTL 60 SET v = ? WHERE k = ?",
                "UPDATE t USING TIMESTAMP ? AND TTL ? SET v = ? WHERE k = ?",
                "",
            })
    void aStatementThatChangesStateOnReplayIsNotIdempotent(String cql) {
        assertFalse(StatementIdempotence.isIdempotent(cql), cql);
    }

    @Test
    void stripsStringAndBlobLiterals() {
        assertEquals(
                "INSERT INTO t (k, v, w) VALUES (?, ?, ?)",
                StatementIdempotence.withoutLiterals("INSERT INTO t (k, v, w) VALUES (0x0aFF, 'a''b', ?)"));
    }

    @Test
    void stripsCommentsAndDollarStringsAndUnquotesPlainNames() {
        assertEquals(
                "UPDATE t SET C0 = ?, \"a b\" = ?    \n WHERE k = ?",
                StatementIdempotence.withoutLiterals(
                        "UPDATE t SET \"C0\" = $$x$$, \"a b\" = ? /* c */ -- d\n WHERE k = ?"));
    }

    @Test
    void classifiesAnUnpreparedQueryWithLargeBlobsInLinearTime() {
        String blob = "0x" + "ab".repeat(64 * 1024);
        StringBuilder insert = new StringBuilder("INSERT INTO \"standard1\" (\"key\"");
        StringBuilder counter = new StringBuilder("UPDATE \"counter1\" SET ");
        for (int i = 0; i < 5; i++) {
            insert.append(",\"C").append(i).append('"');
            counter.append(i > 0 ? "," : "")
                    .append("\"C")
                    .append(i)
                    .append("\"=\"C")
                    .append(i)
                    .append("\"+1");
        }
        insert.append(") VALUES (").append(blob);
        for (int i = 0; i < 5; i++) {
            insert.append(',').append(blob);
        }
        insert.append(')');
        counter.append(" WHERE KEY=").append(blob);

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            assertTrue(StatementIdempotence.isIdempotent(insert.toString()));
            assertFalse(StatementIdempotence.isIdempotent(counter.toString()));
        });
    }
}

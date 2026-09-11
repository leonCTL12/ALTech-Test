package dev.wallet;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FlywaySchemaTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void flywayRecordsTheMigrationAsSuccessful() {
		String version = jdbc.queryForObject(
				"SELECT \"version\" FROM \"flyway_schema_history\" WHERE \"success\" = TRUE AND \"version\" = '1'",
				String.class);
		assertThat(version).isEqualTo("1");
	}

	@Test
	void allThreeTablesExistWithExpectedColumns() {
		assertThat(columnsOf("PLAYER")).containsExactlyInAnyOrder("ID");
		assertThat(columnsOf("WALLET")).containsExactlyInAnyOrder("ID", "PLAYER_ID", "BALANCE", "VERSION");
		assertThat(columnsOf("LEDGER_ENTRY")).containsExactlyInAnyOrder(
				"ID", "WALLET_ID", "AMOUNT", "DIRECTION", "REASON_KIND",
				"DESCRIPTION", "REQUEST_ID", "ORIGINAL_LEDGER_ENTRY_ID", "CREATED_AT");
	}

	@Test
	void walletHasUniqueConstraintOnPlayerId() {
		assertThat(uniqueConstraintsOf("WALLET")).contains("UQ_WALLET_PLAYER");
	}

	@Test
	void ledgerEntryHasUniqueConstraintsOnRequestIdAndOriginalLedgerEntryId() {
		assertThat(uniqueConstraintsOf("LEDGER_ENTRY"))
				.contains("UQ_LEDGER_REQUEST_ID", "UQ_LEDGER_ORIGINAL_LEDGER_ENTRY_ID");
	}

	@Test
	void ledgerRequestIdUniquenessIsScopedPerWallet() {
		assertThat(uniqueConstraintColumnsOf("LEDGER_ENTRY", "UQ_LEDGER_REQUEST_ID"))
				.containsExactlyInAnyOrder("WALLET_ID", "REQUEST_ID");
	}

	private Set<String> columnsOf(String table) {
		return jdbc.queryForList(
						"SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE UPPER(TABLE_NAME) = UPPER(?)",
						String.class, table)
				.stream().map(String::toUpperCase).collect(Collectors.toSet());
	}

	private Set<String> uniqueConstraintsOf(String table) {
		return jdbc.queryForList(
						"SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
								+ "WHERE UPPER(TABLE_NAME) = UPPER(?) AND CONSTRAINT_TYPE = 'UNIQUE'",
						String.class, table)
				.stream().map(String::toUpperCase).collect(Collectors.toSet());
	}

	private Set<String> uniqueConstraintColumnsOf(String table, String constraintName) {
		return jdbc.queryForList(
						"SELECT k.COLUMN_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE k "
								+ "JOIN INFORMATION_SCHEMA.TABLE_CONSTRAINTS c "
								+ "ON k.CONSTRAINT_NAME = c.CONSTRAINT_NAME AND k.TABLE_NAME = c.TABLE_NAME "
								+ "WHERE UPPER(k.TABLE_NAME) = UPPER(?) AND UPPER(k.CONSTRAINT_NAME) = UPPER(?) "
								+ "AND c.CONSTRAINT_TYPE = 'UNIQUE'",
						String.class, table, constraintName)
				.stream().map(String::toUpperCase).collect(Collectors.toSet());
	}
}

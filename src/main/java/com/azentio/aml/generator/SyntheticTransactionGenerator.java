package com.azentio.aml.generator;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

import com.azentio.aml.scenario.Channel;
import com.azentio.aml.scenario.Direction;
import com.azentio.aml.scenario.Transaction;

public final class SyntheticTransactionGenerator {

	private static final String STRUCTURING_ACCOUNT = "900000000001";
	private static final String STRUCTURING_DECOY_ACCOUNT = "900000000002";
	private static final String RAPID_IN_OUT_ACCOUNT = "900000000003";
	private static final String RAPID_IN_OUT_DECOY_ACCOUNT = "900000000004";
	private static final String CSV_HEADER =
			"txnId,accountNumber,txnTime,amount,direction,channel,counterpartyAccount";

	private SyntheticTransactionGenerator() {
	}

	public static void main(String[] args) throws IOException {
		Arguments arguments = Arguments.parse(args);
		generate(arguments.rows(), arguments.seed(), arguments.asOf(), arguments.output(), arguments.groundTruth());
		System.out.printf(
				"Wrote %d synthetic transactions to %s and planted-account truth to %s%n",
				arguments.rows(), arguments.output(), arguments.groundTruth());
	}

	public static void generate(int rows, long seed, Instant asOf, Path output, Path groundTruth) throws IOException {
		if (rows < 9) {
			throw new IllegalArgumentException("rows must be at least 9 to include the planted patterns and decoys");
		}

		List<Transaction> planted = plantedTransactions(asOf);
		writeCsv(rows, planted, seed, asOf, output);
		writeGroundTruth(groundTruth);
	}

	private static Transaction randomTransaction(int index, SplittableRandom random, Instant asOf) {
		String account = accountNumber(200_000_000_000L + index);
		String counterparty = accountNumber(300_000_000_000L + index);
		long secondsAgo = random.nextLong(Duration.ofDays(30).toSeconds());
		int amount = random.nextInt(1, 500_000);
		Direction direction = random.nextBoolean() ? Direction.CREDIT : Direction.DEBIT;
		Channel[] channels = Channel.values();
		Channel channel = channels[random.nextInt(channels.length)];
		return new Transaction(
				"txn-" + String.format(Locale.ROOT, "%010d", index),
				account,
				asOf.minusSeconds(secondsAgo),
				BigDecimal.valueOf(amount, 0).setScale(2),
				direction,
				channel,
				counterparty);
	}

	private static Transaction cash(String id, String account, Instant time, String amount) {
		return new Transaction(id, account, time, new BigDecimal(amount),
				Direction.CREDIT, Channel.CASH, "700000000001");
	}

	private static Transaction transfer(String id, String account, Instant time, String amount, Direction direction) {
		return new Transaction(id, account, time, new BigDecimal(amount),
				direction, Channel.NEFT, "700000000002");
	}

	private static String accountNumber(long value) {
		return String.format(Locale.ROOT, "%012d", value);
	}

	private static List<Transaction> plantedTransactions(Instant asOf) {
		return new ArrayList<>(List.of(
				cash("plant-cash-1", STRUCTURING_ACCOUNT, asOf.minus(Duration.ofHours(72)), "850000.00"),
				cash("plant-cash-2", STRUCTURING_ACCOUNT, asOf.minus(Duration.ofHours(36)), "900000.00"),
				cash("plant-cash-3", STRUCTURING_ACCOUNT, asOf.minus(Duration.ofHours(1)), "950000.00"),
				cash("decoy-cash-1", STRUCTURING_DECOY_ACCOUNT, asOf.minus(Duration.ofHours(24)), "850000.00"),
				cash("decoy-cash-2", STRUCTURING_DECOY_ACCOUNT, asOf.minus(Duration.ofHours(2)), "900000.00"),
				transfer("plant-rapid-in", RAPID_IN_OUT_ACCOUNT, asOf.minus(Duration.ofHours(3)),
						"1000000.00", Direction.CREDIT),
				transfer("plant-rapid-out", RAPID_IN_OUT_ACCOUNT, asOf.minus(Duration.ofHours(1)),
						"900000.00", Direction.DEBIT),
				transfer("decoy-rapid-in", RAPID_IN_OUT_DECOY_ACCOUNT, asOf.minus(Duration.ofHours(3)),
						"1000000.00", Direction.CREDIT),
				transfer("decoy-rapid-out", RAPID_IN_OUT_DECOY_ACCOUNT, asOf.minus(Duration.ofHours(1)),
						"850000.00", Direction.DEBIT)));
	}

	private static void writeCsv(int rows, List<Transaction> planted, long seed, Instant asOf, Path output)
			throws IOException {
		createParent(output);
		SplittableRandom random = new SplittableRandom(seed);
		try (BufferedWriter writer = Files.newBufferedWriter(output)) {
			writer.write(CSV_HEADER);
			writer.newLine();
			for (Transaction transaction : planted) {
				writeTransaction(writer, transaction);
			}
			for (int index = 0; index < rows - planted.size(); index++) {
				writeTransaction(writer, randomTransaction(index, random, asOf));
			}
		}
	}

	private static void writeTransaction(BufferedWriter writer, Transaction transaction) throws IOException {
		writer.write(String.join(",",
				transaction.txnId(),
				transaction.accountNumber(),
				transaction.txnTime().toString(),
				transaction.amount().toPlainString(),
				transaction.direction().name(),
				transaction.channel().name(),
				transaction.counterpartyAccount()));
		writer.newLine();
	}

	private static void writeGroundTruth(Path output) throws IOException {
		createParent(output);
		String truth = """
				{
				  "CASH_STRUCTURING": {
				    "plantedAccounts": ["%s"],
				    "nearMissAccounts": ["%s"]
				  },
				  "RAPID_IN_OUT": {
				    "plantedAccounts": ["%s"],
				    "nearMissAccounts": ["%s"]
				  }
				}
				""".formatted(
				STRUCTURING_ACCOUNT,
				STRUCTURING_DECOY_ACCOUNT,
				RAPID_IN_OUT_ACCOUNT,
				RAPID_IN_OUT_DECOY_ACCOUNT);
		Files.writeString(output, truth);
	}

	private static void createParent(Path path) throws IOException {
		Path parent = path.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
	}

	private record Arguments(int rows, long seed, Instant asOf, Path output, Path groundTruth) {

		private static Arguments parse(String[] args) {
			String rows = null;
			String seed = "42";
			String asOf = Instant.now().toString();
			String output = "data/transactions.csv";
			String groundTruth = "data/ground-truth.json";
			for (int index = 0; index < args.length; index += 2) {
				if (index + 1 >= args.length) {
					throw new IllegalArgumentException("Every option must have a value");
				}
				switch (args[index]) {
					case "--rows" -> rows = args[index + 1];
					case "--seed" -> seed = args[index + 1];
					case "--as-of" -> asOf = args[index + 1];
					case "--output" -> output = args[index + 1];
					case "--ground-truth" -> groundTruth = args[index + 1];
					default -> throw new IllegalArgumentException("Unknown option: " + args[index]);
				}
			}
			if (rows == null) {
				throw new IllegalArgumentException("--rows is required");
			}
			return new Arguments(
					Integer.parseInt(rows),
					Long.parseLong(seed),
					Instant.parse(asOf),
					Path.of(output),
					Path.of(groundTruth));
		}
	}
}

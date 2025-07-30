package org.gagatek.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * A command-line interface (CLI) for the Gagatek Converter tool.
 * This class is responsible for parsing command-line arguments and orchestrating the conversion process.
 */
public class GagatekCli {

    /**
     * A record to hold the parsed configuration from command-line arguments.
     *
     * @param inputFile The path to the source CSV file.
     * @param separator The character or string used to separate columns in the CSV.
     */
    private record Config(Path inputFile, String separator) {}

    /**
     * The main entry point for the application.
     *
     * @param args Command-line arguments provided by the user.
     */
    public static void main(String[] args) {
        Optional<Config> configOpt = parseArgs(args);

        if (configOpt.isEmpty()) {
            printUsage();
            System.exit(1); // Exit with an error code
            return;
        }

        Config config = configOpt.get();
        System.out.println("Starting conversion for file: " + config.inputFile());
        System.out.println("Using separator: '" + config.separator() + "'");

        try {
            GagatekConverter converter = new GagatekConverter(config.inputFile(), config.separator());
            converter.convert();
            System.out.println("✅ Conversion completed successfully!");
        } catch (Exception e) {
            System.err.println("❌ An unexpected error occurred during conversion: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Prints the usage instructions for the command-line tool.
     */
    private static void printUsage() {
        System.out.println("""
        USAGE:
          java -jar gagatek-cli.jar [options] <path_to_csv_file>

        OPTIONS:
          -s=<char>   Specifies the column separator. Default is ';'.

        EXAMPLE:
          java -jar gagatek-cli.jar -s=, data/my_file.csv
        """);
    }

    /**
     * Parses the command-line arguments into a Config object.
     *
     * @param args The array of command-line arguments.
     * @return An Optional containing the Config if parsing is successful, otherwise an empty Optional.
     */
    private static Optional<Config> parseArgs(String[] args) {
        if (args.length == 0) {
            return Optional.empty();
        }

        String separator = ";"; // Default separator
        String filePath = null;

        for (String arg : args) {
            if (arg.startsWith("-s=")) {
                separator = arg.substring(3);
            } else if (!arg.startsWith("-")) {
                filePath = arg;
            }
        }

        if (filePath == null) {
            System.err.println("Error: Input file path is missing.");
            return Optional.empty();
        }

        Path inputFile = Paths.get(filePath);
        if (!Files.exists(inputFile) || !Files.isReadable(inputFile)) {
            System.err.println("Error: File does not exist or is not readable: " + filePath);
            return Optional.empty();
        }

        return Optional.of(new Config(inputFile, separator));
    }
}
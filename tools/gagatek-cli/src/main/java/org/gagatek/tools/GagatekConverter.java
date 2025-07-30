package org.gagatek.tools;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles the core logic of converting a CSV file into a compressed binary format.
 * The process involves two main steps:
 * 1. Dictionary Encoding: Replaces unique string values in each column with integer IDs.
 * 2. Bit-Packing: Compresses the integer IDs into a binary file using a minimal number of bits.
 */
public class GagatekConverter { 

    private final Path sourceFile;
    private final String separator;

    // A simple record to hold the dictionaries for all columns.
    private record Dictionaries(
            List<Map<String, Integer>> valueToIdMaps,
            List<Map<Integer, String>> idToValueMaps
    ) {}

    /**
     * Constructs a new GagatekConverter.
     *
     * @param sourceFile The source CSV file to be converted.
     * @param separator  The column separator used in the CSV file.
     */
    public GagatekConverter(Path sourceFile, String separator) {
        this.sourceFile = sourceFile;
        this.separator = separator;
    }

    /**
     * Executes the full conversion process.
     *
     * @throws IOException if any file I/O error occurs.
     */
    public void convert() throws IOException {
        String baseName = sourceFile.toString();
        final String encodedIdsFile = baseName + ".gag";
        final String binaryFile = baseName + ".gaga";
        final String idToValueDictFile = baseName + ".gagc";
        final String valueToIdDictFile = baseName + ".gagd";

        // Step 1: Encode file to IDs and build dictionaries.
        Dictionaries dictionaries = encodeFileAndBuildDictionaries(encodedIdsFile);
        System.out.println("Step 1/3: File encoded and dictionaries built.");

        // Step 2: Save dictionaries for later use.
        serializeObject(dictionaries.idToValueMaps(), idToValueDictFile);
        serializeObject(dictionaries.valueToIdMaps(), valueToIdDictFile);
        System.out.println("Step 2/3: Dictionaries saved to disk.");

        // Step 3: Pack the encoded ID file into a compact binary format.
        packEncodedFileToBinary(encodedIdsFile, binaryFile, dictionaries.idToValueMaps());
        System.out.println("Step 3/3: Binary file packed successfully.");
    }

    /**
     * Reads the source CSV, replaces values with integer IDs, and writes them to a temporary file.
     *
     * @param destinationPath Path for the intermediate file with encoded IDs.
     * @return A Dictionaries object containing the generated forward and reverse maps.
     * @throws IOException if an I/O error occurs.
     */
    private Dictionaries encodeFileAndBuildDictionaries(String destinationPath) throws IOException {
        var valueToIdMaps = new ArrayList<Map<String, Integer>>();
        var idToValueMaps = new ArrayList<Map<Integer, String>>();

        try (var reader = new BufferedReader(new FileReader(sourceFile.toFile()));
             var writer = new BufferedWriter(new FileWriter(destinationPath))) {

            String line = reader.readLine();
            if (line == null) {
                return new Dictionaries(valueToIdMaps, idToValueMaps); // Handle empty file
            }

            // Initialize a map for each column based on the header.
            String[] header = line.split(separator);
            for (int i = 0; i < header.length; i++) {
                valueToIdMaps.add(new HashMap<>());
                idToValueMaps.add(new HashMap<>());
            }

            // Process data rows
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(separator);
                var encodedLine = new StringBuilder();
                
                for (int i = 0; i < values.length; i++) {
                    final int columnIndex = i;                    
                    String value = values[columnIndex];
                    Map<String, Integer> valueToIdMap = valueToIdMaps.get(columnIndex);

                    Integer id = valueToIdMap.computeIfAbsent(value, k -> {
                        int newId = valueToIdMap.size();
                        idToValueMaps.get(columnIndex).put(newId, k);
                        return newId;
                    });
                    if (columnIndex > 0) encodedLine.append(separator);
                    encodedLine.append(id);
                }
                
                writer.write(encodedLine.toString());
                writer.newLine();
            }
        }
        return new Dictionaries(valueToIdMaps, idToValueMaps);
    }

    /**
     * Packs the ID-encoded data into a binary file, using a minimal number of bits for each column.
     *
     * @param sourcePath    Path to the intermediate file containing integer IDs.
     * @param destinationPath Path for the final compressed binary file.
     * @param idToValueMaps   List of dictionaries, used to calculate the required bits per column.
     * @throws IOException if an I/O error occurs.
     */
    private void packEncodedFileToBinary(String sourcePath, String destinationPath, List<Map<Integer, String>> idToValueMaps) throws IOException {
        int[] bitsPerColumn = new int[idToValueMaps.size()];
        for (int i = 0; i < idToValueMaps.size(); i++) {
            int dictSize = idToValueMaps.get(i).size();
            // This is a fast way to calculate ceil(log2(n)).
            bitsPerColumn[i] = dictSize <= 1 ? 1 : 32 - Integer.numberOfLeadingZeros(dictSize - 1);
        }

        try (var reader = new BufferedReader(new FileReader(sourcePath));
             var writer = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(destinationPath)))) {

            long bitBuffer = 0L;
            int bitsUsed = 0;
            String line;

            while ((line = reader.readLine()) != null) {
                String[] ids = line.split(separator);
                for (int i = 0; i < ids.length; i++) {
                    long value = Long.parseLong(ids[i]);
                    int bitsForValue = bitsPerColumn[i];

                    if (bitsUsed + bitsForValue > 64) {
                        writer.writeLong(bitBuffer);
                        bitBuffer = 0L;
                        bitsUsed = 0;
                    }

                    bitBuffer |= (value << bitsUsed);
                    bitsUsed += bitsForValue;
                }
            }

            if (bitsUsed > 0) {
                writer.writeLong(bitBuffer);
            }
        }
    }

    /**
     * Serializes a Java object to a file.
     *
     * @param object   The object to serialize.
     * @param filePath The path to the destination file.
     * @throws IOException if an I/O error occurs.
     */
    private void serializeObject(Object object, String filePath) throws IOException {
        try (var oos = new ObjectOutputStream(new FileOutputStream(filePath))) {
            oos.writeObject(object);
        }
    }
}
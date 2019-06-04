package btp.hd.intercom_row.util;

import java.io.*;
import java.util.Objects;
import java.util.StringTokenizer;

public class PgmReader {

    private static final String TEMP_TXT = "temp.txt";
    private static final String COND_TXT = "cond.txt";

    public static double[][] getTempValues() {
        return read(TEMP_TXT);
    }

    public static double[][] getCondValues() {
        return read(COND_TXT);
    }

    private static double[][] read(String fileName) {
        int height = 0;
        int width = 0;
        double[][] matrix;

        BufferedReader br = openBufferedReader(fileName);

        try { // read dimensions
            StringTokenizer dimensions = new StringTokenizer(br.readLine());
            height = Integer.parseInt(dimensions.nextToken());
            width = Integer.parseInt(dimensions.nextToken());
        } catch (IOException e) {
            System.err.println("Invalid integer found!");
            System.exit(1);
        }

        matrix = new double[height][width];

        try { // read values
            for (int i = 0; i < height; i++) {
                StringTokenizer row = new StringTokenizer(br.readLine());

                for (int j = 0; j < width; j++) {
                    matrix[i][j] = Double.parseDouble(row.nextToken());
                }
            }
        } catch (IOException e) {
            System.err.println("Invalid double found!");
            System.exit(1);
        }

        return matrix;
    }

    private static BufferedReader openBufferedReader(String fileName) {
        try {
            return new BufferedReader(getResourceReader(fileName));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return null; // ignore
    }

    private static Reader getResourceReader(String fileName) throws FileNotFoundException {
        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(fileName);

        if (Objects.isNull(inputStream)) {
            throw new FileNotFoundException(String.format("File '{}' not found", fileName));
        }

        return new InputStreamReader(inputStream);
    }

}

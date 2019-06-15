package btp.hd.intercom_row.util;

import java.io.*;
import java.util.StringTokenizer;

public class PgmReader {

  private static final String TEMP = "plasma";
  private static final String COND = "pat2";

  public static double[][] getTempValues(String fileDir, int height, int width) {
    try {
      return read(fileDir + "/" + TEMP, height, width);
    } catch (IOException e) {
      e.printStackTrace();
    }

    return null;
  }

  public static double[][] getCondValues(String fileDir, int height, int width) {
    try {
      return read(fileDir + "/" + COND, height, width);
    } catch (IOException e) {
      e.printStackTrace();
    }

    return null;
  }

  private static double[][] read(String fileDir, int height, int width) throws IOException {
    double[][] matrix;
    String fileName = String.format("%s_%dx%d.pgm", fileDir, height, width);

    BufferedReader br = openBufferedReader(fileName);
    br.readLine(); // ignore "P2"?

    StringTokenizer dimensions = new StringTokenizer(br.readLine());
    height = Integer.parseInt(dimensions.nextToken());
    width = Integer.parseInt(dimensions.nextToken());

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
      return new BufferedReader(new FileReader(fileName));
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }

    return null; // ignore
  }

//    private static Reader getResourceReader(String fileName) throws FileNotFoundException {
//        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
//        InputStream inputStream = classLoader.getResourceAsStream(fileName);
//
//        if (Objects.isNull(inputStream)) {
//            throw new FileNotFoundException(String.format("File '{}' not found", fileName));
//        }
//
//        return new InputStreamReader(inputStream);
//    }

}

package btp.hd.intercom_row.util;

import java.io.*;
import java.util.StringTokenizer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PgmReader {

  private static final String TEMP = "plasma";
  private static final String COND = "pat2";

  public static double[][] getTempValues(String fileDir, int height, int width) {
    try {
      return read(fileDir + TEMP, height, width, 100, 100);
    } catch (IOException e) {
      e.printStackTrace();
    }

    return null;
  }

  public static double[][] getCondValues(String fileDir, int height, int width) {
    try {
      return read(fileDir + COND, height, width, 0, 1);
    } catch (IOException e) {
      e.printStackTrace();
    }

    return null;
  }

  private static double[][] read(String fileDir, int height, int width, int min, int max) throws IOException {
    double[][] matrix;
    String fileName = String.format("%s_%dx%d.pgm", fileDir, height, width);

    log.info("Reading file from dir: {}", fileDir);

    BufferedReader br = openBufferedReader(fileName);

    br.readLine(); // ignore "P2"?
    StringTokenizer dimensions = new StringTokenizer(br.readLine());
    height = Integer.parseInt(dimensions.nextToken());
    width = Integer.parseInt(dimensions.nextToken());
    double maxValue = Double.parseDouble(br.readLine()); // ignore max heat

    int x = 0;
    int y = 0;
    matrix = new double[height][width];

    do {
      StringTokenizer row = new StringTokenizer(br.readLine());

      while (row.hasMoreTokens()) {
        matrix[y][x] = min + Double.parseDouble(row.nextToken()) * (max - min) / maxValue;
        x++;

        if (x == width) {
          x = 0;
          y++;
        }
      }
    } while (y < height);

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

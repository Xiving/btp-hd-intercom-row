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
      return read(fileDir + "/" + TEMP, height, width);
    } catch (IOException e) {
      e.printStackTrace();
    }

    return null;
  }

  public static double[][] getCondValues(String fileDir, int height, int width) {
    try {
      return read(fileDir + COND, height, width);
    } catch (IOException e) {
      e.printStackTrace();
    }

    return null;
  }

  private static double[][] read(String fileDir, int height, int width) throws IOException {
    double[][] matrix;
    String fileName = String.format("%s_%dx%d.pgm", fileDir, height, width);

    log.info("Reading file from dir: {}", fileDir);

    BufferedReader br = openBufferedReader(fileName);
    br.readLine(); // ignore "P2"?

    StringTokenizer dimensions = new StringTokenizer(br.readLine());
    height = Integer.parseInt(dimensions.nextToken());
    width = Integer.parseInt(dimensions.nextToken());

    matrix = new double[height][width];

    int x = 0;
    int y = 0;

    do {
      StringTokenizer row = new StringTokenizer(br.readLine());

      while (row.hasMoreTokens()) {
        matrix[y][x] = Double.parseDouble(row.nextToken());
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

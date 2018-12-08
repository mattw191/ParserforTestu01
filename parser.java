/*
  the curent constants are adjusted for the linear acceleration data to be parsed into statistically random bits,
  these constants need to be modified to be used successfully with different sources.

*/

import java.io.*;
import java.util.ArrayList;
import java.util.BitSet;

public class parser {
    public static void main(String[] args) throws Exception {

        if (args.length > 0) {
            File file = new File(args[0]);
            BufferedReader reader = new BufferedReader(new FileReader(file));

            // Holds currently processed line
            String line;
            // Sensor Column, Data Digit, Bin
            int[][][] sums = new int[constants.axes][constants.sigDigs][10];
            // Holds probabilities for the above
            double[][][] probabilities = new double[constants.axes][constants.sigDigs][10];
            boolean[][] valid = new boolean[constants.axes][constants.sigDigs];

            // Counter for number of data lines
            int numlines = 0;
            // Holds column headers from first line of file
            String[] columnHeaders = new String[4];

            long startTime = Long.MAX_VALUE;
            long endTime = 0;

            while ((line = reader.readLine()) != null) {
                // Skip first header line
                if (numlines > 0) {
                    // Split each line by commas
                    String[] columns = line.split(",");

                    long timestamp = Long.parseLong(columns[0]);
                    if (timestamp < startTime) {
                        startTime = timestamp;
                    }
                    if (timestamp > endTime) {
                        endTime = timestamp;
                    }

                    for (int i=1; i < columns.length;i++) {
                        // Take only the decimal portion of the value
                        String newstr = columns[i].split("\\.")[1];

                        for (int k = 0 ; k < sums[0].length ; k++){
                            // "Bin" the digit
                            int val = Integer.parseInt(String.valueOf(newstr.charAt(k)));
                            sums[i-1][k][val] += 1;
                        }
                    }
                }
                else {
                    // Get file headers
                    columnHeaders = line.split(",");
                }
                numlines++;
            }
            reader.close();

            // Remove header line from numlines
            numlines -= 1;

            for (int i = 0; i < sums.length; i++)
            {
                System.out.printf("Axis %1s:", columnHeaders[i+1]);
                for (int bin = 0; bin < 10; bin++) {
                    System.out.printf("%9d", bin);
                }
                System.out.println();
                for (int j = 0; j < sums[i].length; j++) {
                    for (int k = 0; k < sums[i][j].length; k++) {
                        // Compute the frequency as a percentage
                        double p = (double) sums[i][j][k] / (double) numlines;
                        probabilities[i][j][k] = p;
                    }
                    double sd = statistical_distance(probabilities[i][j], constants.q);
                    double kd = kullback_distance(probabilities[i][j], constants.q);
                    double me = min_entropy(probabilities[i][j]);
                    if (kd < constants.kdThreshold && me > constants.entropyThreshold) {
                        valid[i][j] = true;
                        System.out.printf("Digit %2d: ", j);
                        for (int k = 0; k < sums[i][j].length; k++) {
                            System.out.printf("%6.2f%%", probabilities[i][j][k] * 100);
                            if (k < sums[i][j].length - 1) {
                                System.out.print(", ");
                            }
                        }
                        System.out.printf("\nStatistical Distance: %.10f\n", sd);
                        System.out.printf("   Kullback Distance: %.10f\n", kd);
                        System.out.printf("     Minimum Entropy: %.10f bits\n", me);
                    }
                    else {
                        valid[i][j] = false;
                    }
                }
                //System.out.printf("This axis has %d useful digits\n", numgooddigits(i, valid));
                System.out.println();
            }

            int bitsentropy = writeBinary(args[0], valid);

            double elapsedTime = (double) (endTime - startTime) / constants.nanosecondsPerSecond;
            int minutes = (int) elapsedTime / 60;
            int seconds = (int) elapsedTime % 60;
            System.out.printf("Generated %d bits of entropy in %dm %ds\n", bitsentropy, minutes, seconds);
        }
        else {
            System.out.println("Please pass in a file");
        }
    }

    private static int writeBinary(String filename, boolean[][] validDigits)  throws Exception {
        int bitsEntropy = 0;
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        ArrayList<Integer> values = new ArrayList<Integer>();
        String line;
        int numlines = 0;
        while ((line = reader.readLine()) != null) {
            // Skip first header line
            if (numlines > 0) {
                // Split each line by commas
                String[] columns = line.split(",");

                for (int i=1; i < columns.length;i++) {
                    // Take only the decimal portion of the value
                    String newstr = columns[i].split("\\.")[1];

                    for (int k = 0 ; k < constants.sigDigs ; k++){
                        if (validDigits[i-1][k]) {
                            int val = Integer.parseInt(String.valueOf(newstr.charAt(k)));
                            switch (val) {
                                case 0: values.add(0);
                                        values.add(0);
                                        values.add(0);
                                        bitsEntropy += 3;
                                        break;
                                case 1: values.add(0);
                                        values.add(0);
                                        values.add(1);
                                        bitsEntropy += 3;
                                        break;
                                case 2: values.add(0);
                                        values.add(1);
                                        values.add(0);
                                        bitsEntropy += 3;
                                        break;
                                case 3: values.add(0);
                                        values.add(1);
                                        values.add(1);
                                        bitsEntropy += 3;
                                        break;
                                case 4: values.add(1);
                                        values.add(0);
                                        values.add(0);
                                        bitsEntropy += 3;
                                        break;
                                case 5: values.add(1);
                                        values.add(0);
                                        values.add(1);
                                        bitsEntropy += 3;
                                        break;
                                case 6: values.add(1);
                                        values.add(1);
                                        values.add(0);
                                        bitsEntropy += 3;
                                        break;
                                case 9: values.add(1);
                                        values.add(1);
                                        values.add(1);
                                        bitsEntropy += 3;
                                        break;
                            }
                        }
                    }
                }
            }
            numlines++;
        }
        reader.close();
        String outfilename = filename + ".bin";
        FileOutputStream fos = new FileOutputStream(outfilename);
        // Can only write full bytes of output - drop the last few unusable bits
        bitsEntropy -= (bitsEntropy % 8);
        BitSet bits = new BitSet(bitsEntropy);
        for (int i = 0; i < bitsEntropy; i++) {
            if (values.get(i) == 1) {
                bits.set(i, true);
            } else {
                bits.set(i, false);
            }
        }
        fos.write(bits.toByteArray());
        fos.close();
        System.out.printf("Wrote %d bytes (%d bits) to file: \"%s\"\n", bitsEntropy / 8, bitsEntropy, outfilename);

        return bitsEntropy;
    }

    private static double min_entropy(double[] p) {
        double max = 0;
        for (int i = 0; i< p.length; i++) {
            if (p[i] > max) {
                max = p[i];
            }
        }

        return Math.abs(log2(max));
    }

    private static double statistical_distance(double[] p, double q) {
        double sum = 0;
        for (int i = 0; i< p.length; i++) {
            if (p[i] > 0) {
                sum += Math.abs(p[i] - q);
            }
        }
        return (0.5*sum);
    }

    private static double kullback_distance(double[] p, double q){
      double sum = 0;
      for (int i = 0; i< p.length; i++) {
          if (p[i] > 0) {
              sum += p[i] * log2(p[i] / q);
          }
      }

      return sum;
    }

    private static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }

    private static int numgooddigits(int axis, boolean[][] valid) {
        int count = 0;
        for (int i = 0; i < valid[axis].length; i++) {
            if (valid[axis][i]) {
                count++;
            }
        }
        return count;
    }
}

class constants {
    public static int axes = 3;
    public static int sigDigs = 20;
    public static double q = 0.1;
    public static double kdThreshold = 0.00015;
    public static double sdThreshold = 0.0065;  //seems to be good for LA
    public static double entropyThreshold = 3; //at least 3 bits because of our conversion
    public static double nanosecondsPerSecond = 1e9;
}

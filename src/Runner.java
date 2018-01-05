import java.io.InputStream;
import java.util.Scanner;
import java.util.concurrent.Callable;

public class Runner implements Callable<Solution>
{
    private final boolean python;
    private final String exePath;
    private final String instancePath;
    private final String configsPath;

    Runner (boolean python, String exePath, String instancePath, String configsPath)
    {
        this.python = python;
        this.exePath = exePath;
        this.instancePath = instancePath;
        this.configsPath = configsPath;
    }

    @Override
    public Solution call ()
    {
        ProcessBuilder pb;
        String args[];

        if (this.python) {
            args = new String[]{"python3", exePath, "-i", instancePath, "-c", configsPath, "-dp"};
        }
        else {
            args = new String[]{exePath, "-i", instancePath, "-c", configsPath, "-dp"};
        }

        pb = new ProcessBuilder(args);

        try {
            Process process = pb.start();

            InputStream in       = process.getInputStream();
            Scanner     sc       = new Scanner(in);
            String      lastLine = "";
            while (sc.hasNextLine()) {
                lastLine = sc.nextLine();
            }
            in.close();
            sc.close();

            int exitValue = process.waitFor();

            String[] parts = lastLine.split("\t");

            int    score = Integer.parseInt(parts[0]);
            double time  = Double.parseDouble(parts[1].split(" ")[0]);
            int    nbIt  = Integer.parseInt(parts[2].split(" ")[0]);

            System.out.printf("Thread%d : %d - %d\t%.2f s\t%d iterations\n", Thread.currentThread().getId(), exitValue, score, time, nbIt);

            return new Solution(score, time, nbIt);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}

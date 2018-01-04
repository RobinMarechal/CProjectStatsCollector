import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;
import java.util.concurrent.Callable;

public class Runner implements Callable<Solution>
{
    private final boolean python;
    private final String exePath;
    private final String instancePath;
    private final String configsPath;

    public Runner (boolean python, String exePath, String instancePath, String configsPath)
    {
        this.python = python;
        this.exePath = exePath;
        this.instancePath = instancePath;
        this.configsPath = configsPath;
    }

    @Override
    public Solution call () throws Exception
    {
        ProcessBuilder pb;

        if(this.python)
            pb = new ProcessBuilder("python3", exePath, instancePath, configsPath);
        else
            pb = new ProcessBuilder(exePath, instancePath, configsPath);

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

            System.out.println("Thread" + Thread.currentThread().getId() + " : " + exitValue + " - " + lastLine);

            String[] parts = lastLine.split("\t");

            int    score = Integer.parseInt(parts[0]);
            double time  = Double.parseDouble(parts[1].split(" ")[0]);
            int    nbIt  = Integer.parseInt(parts[2].split(" ")[0]);

//            System.out.format("Thread %d\t->\t%d\t%.4f\t%d", Thread.currentThread().getId(), score, time, nbIt);

            return new Solution(score, time, nbIt);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }


        return null;
    }
}

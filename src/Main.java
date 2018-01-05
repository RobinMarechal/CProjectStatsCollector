import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class Main extends Application
{
    private static int nbTurns = 2;
    private static int nbThreads = 1;
    private static String dirPath;
    private static String exePath;
    private static String dataFilesFolderPath;
    private static String fileNameRegexp = "I(_\\d{1,3}){4}\\.txt";
    private static String outputSuffix;
    private static String configsFolderPath;

    private static Which whichProgram = Which.C;

    private static File output = null;

    private static String OS;
    private static String dirSep;

    private static List<File> instanceFiles;
    private static List<File> configFiles;

    private static int remainingTime;

    public static void main (String[] args)
    {
        launch(args);
    }


    @Override
    public void start (Stage windows) throws Exception
    {
        askForProjectDir(windows);
        askForPythonOrC();

        OS = System.getProperty("os.name").toLowerCase();
        dirSep = File.separator;
        final LocalDateTime now = LocalDateTime.now();
        outputSuffix = "_" + now.format(DateTimeFormatter.ofPattern("yyyyMMddhhmmss")) + ".txt";
        dataFilesFolderPath = dirPath + dirSep + "data" + dirSep + "for_java" + dirSep;
        configsFolderPath = dirPath + dirSep + "configs";

        File configsFolder = new File(configsFolderPath);


        Comparator<File> fileNameComparator = (o1, o2) -> {
            String f1Name = o1.getName();
            f1Name = f1Name.split("\\.")[0];
            String f1Parts[]    = f1Name.split("_");
            int    f1NbMachines = Integer.parseInt(f1Parts[2]);
            int    f1NbJobs     = Integer.parseInt(f1Parts[3]);
            int    n1           = Integer.parseInt(f1Parts[4]);

            String f2Name = o2.getName();
            f2Name = f2Name.substring(0, f2Name.length() - 4);
            String f2Parts[]    = f2Name.split("_");
            int    f2NbMachines = Integer.parseInt(f2Parts[2]);
            int    f2NbJobs     = Integer.parseInt(f2Parts[3]);
            int    n2           = Integer.parseInt(f2Parts[4]);


            if (f1NbMachines != f2NbMachines) {
                return f1NbMachines - f2NbMachines;
            }
            else if (f1NbJobs != f2NbJobs) {
                return f1NbJobs - f2NbJobs;
            }
            else {
                return n1 - n2;
            }
        };


        File dataFolder = new File(dataFilesFolderPath);

        if (!dataFolder.exists() || !dataFolder.isDirectory()) {
            System.out.println("Error: data folder doesn't exist...");
            System.exit(1);
        }

        File[] files = dataFolder.listFiles(File::isFile);

        if (files == null) {
            System.out.println("Failed to get instance files...");
            System.exit(1);
        }

        instanceFiles = Arrays.stream(files).filter(file -> file.getName().matches(fileNameRegexp)).sorted(fileNameComparator).collect(Collectors.toList());

        File[] configs = configsFolder.listFiles(File::isFile);

        if (configs == null) {
            System.out.println("Failed to get config files...");
            System.exit(1);
        }

        configFiles = Arrays.stream(configs).sorted(File::compareTo).collect(Collectors.toList());

        nbThreads = OS.contains("win") ? 1 : 2;

        long startTime = System.currentTimeMillis();

        if (whichProgram == Which.BOTH) {
            System.out.println("Running C then Python");
            calcBaseRemainingTime(2);
            run(false);
            run(true);
        }
        else if (whichProgram == Which.PYTHON) {
            System.out.println("Running Python only");
            calcBaseRemainingTime(1);
            run(true);
        }
        else if (whichProgram == Which.C) {
            System.out.println("Running C only");
            calcBaseRemainingTime(1);
            run(false);
        }

        long endTime = System.currentTimeMillis();

        int timePassedSeconds = (int) (startTime - endTime) / 1000;

        System.out.println();
        System.out.println("Actual execution time : " + getTimeString(timePassedSeconds));
        System.out.println();
        
        System.exit(0);
    }

    private void calcBaseRemainingTime (int nb)
    {
        int nbConfigs = configFiles.size();

        for (File inst : instanceFiles) {
            String fileName        = inst.getName();
            String fileNameParts[] = fileName.split("_");
            int    nbMachines      = Integer.parseInt(fileNameParts[2]);
            int    nbJobs          = Integer.parseInt(fileNameParts[3]);

            // The time calculated in the C program
            remainingTime += nbJobs * nbMachines / 4;
        }

        // each file is processed for each config, for (Python && C) or (Python) or (C), nbTurns each, but with nbThreads threads
        remainingTime = remainingTime * nbConfigs * nb * nbTurns / nbThreads;
    }

    private String getTimeString (int nbSeconds)
    {
        int nbHours = nbSeconds / 3600;
        nbSeconds -= nbHours * 3600;
        int nbMinutes = nbSeconds / 60;
        nbSeconds -= nbMinutes * 60;

        return String.format("%dH %dmin %ds", nbHours, nbMinutes, nbSeconds);
    }

    private void updateRemainingTime (File instance)
    {
        String fileName        = instance.getName();
        String fileNameParts[] = fileName.split("_");
        int    nbMachines      = Integer.parseInt(fileNameParts[2]);
        int    nbJobs          = Integer.parseInt(fileNameParts[3]);

        remainingTime -= nbJobs * nbMachines / 4 * (3 - nbThreads);
    }

    private void run (boolean python) throws IOException
    {
        String outputFileName;

        if (python) {
            exePath = dirPath + dirSep + "tabu_python.py";
            outputFileName = "py_";
        }
        else {
            outputFileName = "c_";

            exePath = dirPath + dirSep + "bin" + dirSep + "app" + dirSep + "Release" + dirSep + "DI4_ProjectC";
        }

        if (OS.contains("win")) {
            if (!python) {
                exePath += ".exe";
            }
            outputFileName += "win";
        }
        else {
            outputFileName += "linux";
        }

        String outputFilePath = dirPath + dirSep + "stats" + dirSep + outputFileName;

        outputFilePath += outputSuffix;

        System.out.println();
        System.out.println("Testing " + (python ? "Python" : "C") + " program");
        System.out.println("Exe file : " + exePath);
        System.out.println("Instances folder : " + dataFilesFolderPath);
        System.out.println("Output file : " + outputFilePath);
        System.out.println("Configs folder : " + configsFolderPath);
        System.out.println();

        System.out.println();
        System.out.println("Starting...");
        System.out.println(nbTurns + " runs for " + instanceFiles.size() + " instances and " + configFiles.size() + " configs");
        System.out.println(nbThreads + " threads");
        System.out.println();

        output = new File(outputFilePath);
        File parent = output.getParentFile();

        if (!parent.exists() && !parent.mkdirs()) {
            System.out.println("Failed to create intermediate folders...");
            System.exit(1);
        }

        if (!output.exists() && !output.createNewFile()) {
            System.out.println("Failed to create the output file...");
            System.exit(1);
        }

        instanceFiles.forEach(input -> {
            writeToOutput(input.getName() + "\n");
            configFiles.forEach(config -> runForInstanceFile(input, config, python));
            writeToOutput("\n");
        });
    }

    private void writeToOutput (String line)
    {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(output, true));
            writer.append(line);
            writer.close();
        }
        catch (IOException e) {
            System.out.println("Failed to write to output file : \n\t" + line);
        }
    }


    private void runForInstanceFile (File file, File config, boolean python)
    {
        System.out.println("Instance : " + file.getName());
        System.out.println("Config : " + config.getName());

        updateRemainingTime(file);

        //        System.out.println();
        System.out.println("Estimated max remaining time : " + getTimeString(remainingTime));
        //        System.out.println();

        String format = "{{value}}\t{{time}}\t{{nbIt}}\t{{file}}\n";

        try {
            ExecutorService          executor = Executors.newFixedThreadPool(nbThreads);
            List<Callable<Solution>> tasks    = new ArrayList<>();
            List<Future<Solution>>   futures;

            for (int i = 0; i < nbTurns; i++) {
                Callable<Solution> run = new Runner(python, exePath, file.getAbsolutePath(), config.getAbsolutePath());
                tasks.add(run);
            }

            futures = executor.invokeAll(tasks);
            executor.shutdown();

            int    totalNbIt = 0;
            double totalTime = 0;
            int    score     = Integer.MAX_VALUE;
            int    nbEquals  = 1;

            for (Future<Solution> future : futures) {
                Solution sol      = future.get();
                int      solScore = sol.getScore();

                if (solScore < score) {
                    score = solScore;
                    totalNbIt = sol.getNbIt();
                    totalTime = sol.getTime();
                    nbEquals = 1;
                }
                else if (solScore == score) {
                    totalNbIt += sol.getNbIt();
                    totalTime += sol.getTime();
                    nbEquals++;
                }
            }

            double avgTime = totalTime / (double) nbEquals;
            int    avgNbIt = (int) (totalNbIt / (double) nbEquals);

            String line = format.replace("{{file}}", config.getName())
                                .replace("{{value}}", score + "")
                                .replace("{{time}}", String.format("%.2f", avgTime))
                                .replace("{{nbIt}}", avgNbIt + "");

            System.out.println("\t-> " + line);
            System.out.println();
            writeToOutput(line);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void askForPythonOrC ()
    {
        Stage  stage   = new Stage();
        Button cBtn    = new Button("C");
        Button pyBtn   = new Button("Python");
        Button bothBtn = new Button("Both");
        HBox   hbox    = new HBox(20, cBtn, pyBtn, bothBtn);

        stage.setTitle("Select the program to run");
        hbox.setPadding(new Insets(20));
        cBtn.setPrefWidth(100);
        pyBtn.setPrefWidth(100);
        bothBtn.setPrefWidth(100);

        stage.setScene(new Scene(hbox));

        cBtn.setOnAction(event -> {
            whichProgram = Which.C;
            stage.close();
        });

        pyBtn.setOnAction(event -> {
            whichProgram = Which.PYTHON;
            stage.close();
        });

        bothBtn.setOnAction(event -> {
            whichProgram = Which.BOTH;
            stage.close();
        });

        stage.showAndWait();
    }

    private void askForProjectDir (Stage mainWindows)
    {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Open Project Folder");
        chooser.setInitialDirectory(new File(System.getProperty("user.home")));

        File dir = chooser.showDialog(mainWindows);
        if (dir == null) {
            System.exit(0);
        }

        dirPath = dir.getAbsolutePath();
    }
}
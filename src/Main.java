import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

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
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class Main extends Application
{
    private static int nbTurns = 4;
    private static int nbThreads = 2;
    private static String dirPath;
    private static String exePath;
    private static String dataFilesFolderPath;
    private static String outputFilePath;
    private static String fileNameRegexp = "I(_\\d{1,3}){4}\\.txt";
    private static String configsFolderPath;

    private static boolean python = false;

    private static File output = null;
    private static BufferedWriter writer = null;

    public static void main (String[] args) throws IOException
    {
        launch(args);
    }


    @Override
    public void start (Stage windows) throws Exception
    {
        askForProjectDir(windows);
        askForPythonOrC();

        final String OS     = System.getProperty("os.name").toLowerCase();
        final String dirSep = File.separator;
        String       outputFileName;


        dataFilesFolderPath = dirPath + dirSep + "data" + dirSep;

        if (python) {
            exePath = dirPath + dirSep + "tabu_python.py";
            outputFileName = "py_";
        }
        else {
            outputFileName = "c_";

            exePath = dirPath + dirSep + "bin" + dirSep + "app" + dirSep + "Release" + dirSep + "DI4_ProjectC";
        }

        if (OS.indexOf("win") >= 0) {
            if (!python) {
                exePath += ".exe";
            }
            outputFileName += "win";
        }
        else {
            outputFileName += "linux";
        }

        outputFilePath = dirPath + dirSep + "stats" + dirSep + outputFileName;

        outputFilePath += "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddhhmmss")) + ".txt";
        configsFolderPath = dirPath + dirSep + "configs";

        System.out.println();
        System.out.println("Testing " + (python ? "python" : "C") + " program");
        System.out.println("Exe file : " + exePath);
        System.out.println("Instances folder : " + dataFilesFolderPath);
        System.out.println("Ouput file : " + outputFilePath);
        System.out.println("Configs folder : " + configsFolderPath);
        System.out.println();

        System.exit(0);

        File dataFolder = new File(dataFilesFolderPath);

        if (!dataFolder.exists() || !dataFolder.isDirectory()) {
            System.out.println("Error: data folder doesn't exist...");
            System.exit(1);
        }

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

        File[] files = dataFolder.listFiles();
        List<File> fileList = Arrays.asList(files)
                                    .stream()
                                    .filter(file -> file.getName().matches(fileNameRegexp))
                                    .sorted(fileNameComparator)
                                    .collect(Collectors.toList());

        File[]     configs     = configsFolder.listFiles();
        List<File> configsList = Arrays.asList(configs).stream().sorted(File::compareTo).collect(Collectors.toList());

        //        for (File file : fileList) {
        //            System.out.println(file.getName());
        //        }

        List<File> testSubList = fileList;

        System.out.println();
        System.out.println("Starting...");
        System.out.println(nbTurns + " runs for " + testSubList.size() + " instances");
        System.out.println();

        output = new File(outputFilePath);
        File parent = output.getParentFile();

        if (!parent.exists()) {
            parent.mkdirs();
        }

        if (!output.exists()) {
            output.createNewFile();
        }

        testSubList.forEach(input -> configsList.forEach(config -> runForInstanceFile(input, config)));

        System.exit(0);
    }


    public static void runForInstanceFile (File file, File config)
    {
        System.out.println("Running for instance '" + file.getName() + "'");
        String format = "{{file}}\t{{value}}\t{{time}}\t{{nbIt}}";

        try {
            writer = new BufferedWriter(new FileWriter(output, true));

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

            String line = format.replace("{{file}}", file.getName())
                                .replace("{{value}}", score + "")
                                .replace("{{time}}", String.format("%.2f", avgTime))
                                .replace("{{nbIt}}", avgNbIt + "");

            System.out.println("\t-> " + line);
            System.out.println();
            writer.append(line + "\n");
            writer.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
        catch (ExecutionException e) {
            e.printStackTrace();
        }
    }


    private void askForPythonOrC ()
    {
        Stage  stage = new Stage(StageStyle.UTILITY);
        Button cBtn  = new Button("C");
        Button pyBtn = new Button("Python");
        HBox   hbox  = new HBox(20, cBtn, pyBtn);

        stage.setTitle("Select the program to run");
        hbox.setPadding(new Insets(20));
        cBtn.setPrefWidth(100);
        pyBtn.setPrefWidth(100);

        stage.setScene(new Scene(hbox));

        cBtn.setOnAction(event -> {
            python = false;
            stage.close();
        });

        pyBtn.setOnAction(event -> {
            python = true;
            stage.close();
        });

        stage.showAndWait();
    }

    public static void askForProjectDir (Stage mainWindows)
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


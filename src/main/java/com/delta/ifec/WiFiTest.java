package com.delta.ifec;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import org.apache.commons.cli.*;

import java.io.*;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.time.ZonedDateTime.now;

/* class to demonstrate use of Drive files list API */
public class WiFiTest {
    /**
     * Application name.
     */
    private static final String APPLICATION_NAME = "WiFi Content Load Test";
    /**
     * Global instance of the JSON factory.
     */
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    /**
     * Directory to store authorization tokens for this application.
     */
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = new ArrayList(DriveScopes.all());
           // Collections.singletonList(DriveScopes.DRIVE_METADATA_READONLY);
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

    private static final String CONTENT_DIR = "TestContent";

    private static int totalSize = 0;
    private static int blockSize = 0;
    private static boolean uploadFlag = false;
    private static String  tagLine = "Unspecified";
    private static Duration randMin = Duration.ZERO;

    private static void parseArgs(String[] args) {
        Options options = new Options();

        options.addOption("U",false,"Specify to Upload Data instead of Download");

        Option xferSize = Option.builder("s")
                .argName("totalSize")
                .hasArg(true)
                .desc("Total Size to transfer")
                .build();
        options.addOption(xferSize);

        Option blkSize = Option.builder("b")
                .argName("blockSize")
                .hasArg(true)
                .desc("Block size in MB")
                .build();
        options.addOption(blkSize);

        Option tag = Option.builder("t")
                .argName("tag")
                .hasArg(true)
                .required(true)
                .build();
        options.addOption(tag);

        Option rnd = Option.builder("r")
                .argName("randMin")
                .hasArg(true)
                .type(Integer.class)
                .build();
        options.addOption(rnd);
        CommandLineParser parser = new DefaultParser();

        try {
            CommandLine cmd = parser.parse(options, args);

            uploadFlag = cmd.hasOption("U");
            totalSize = cmd.getParsedOptionValue("totalSize",2*1024);
            blockSize = cmd.getParsedOptionValue("blockSize",1);
            tagLine = cmd.getParsedOptionValue("t");

            if (cmd.hasOption("r")) {
                String val = cmd.getOptionValue("r");

                int v = Integer.parseInt(val);
                randMin = Duration.ofMinutes(v);
            }

        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

    }



    /**
     * Creates an authorized Credential object.
     *
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT)
            throws IOException {
        // Load client secrets.
        InputStream in = WiFiTest.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
        //returns an authorized Credential object.
        return credential;
    }

    private static File getFile(final List<File> catalog,final String fileName) {
        for (File f: catalog) {
            if (f.getName().equals(fileName)) {
                return f;
            }
        }
        return null;
    }

    private static BufferedOutputStream createLogFile() throws IOException {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

        String logFilename = String.format("LOG_%s.txt",
                                    formatter.format(ZonedDateTime.now()));

        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(logFilename));
        bos.write(tagLine.getBytes());

        return bos;

    }

    public static void main(String... args) throws IOException, GeneralSecurityException {
        // Build a new authorized API client service.
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        Drive service = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();

        parseArgs(args);


        try (BufferedOutputStream bos = createLogFile()) {

            FileList top = service.files().list().setQ("mimeType='application/vnd.google-apps.folder'").execute();
            List<File> files = top.getFiles();
            File contentDir = getFile(files, CONTENT_DIR);

            if (uploadFlag) {
                Upload  ulTest = new Upload(service,contentDir.getId());
                ulTest.run(128,1*1024*1024);
            }



            DownloadTest dlTest = new DownloadTest(service, contentDir, bos);

            ZonedDateTime start = ZonedDateTime.now();


            double total = 0;

            if (randMin.compareTo(Duration.ZERO) != 0) {
                total = dlTest.runRandom(randMin);
            }
            else {
                total = dlTest.run(totalSize,blockSize);
            }

            Duration duration = Duration.between(start, ZonedDateTime.now());
            double mbXferred = total / (1024.0 * 1024.0);

            float dur = duration.toSeconds();
            System.out.printf("%4.2f MB in %02d:%02d = %5.2f MB/s \n",
                               mbXferred,
                               duration.toMinutesPart(),
                               duration.toSecondsPart(),
                               mbXferred/duration.toSeconds());


        } catch (IOException e) {
            e.printStackTrace();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

    }
}
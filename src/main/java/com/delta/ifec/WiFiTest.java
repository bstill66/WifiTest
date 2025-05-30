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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static Long totalSize = null;
    private static Long blockSize = null;
    private static boolean uploadFlag = false;

    private static Duration randMin = Duration.ZERO;
    private static String gate = "XX";
    private static String airport = "ATL";

    private static long cvtBytes(final String val) throws ParseException {
        final Pattern regex = Pattern.compile("([0-9]+)([BKMG])");

        long numBytes = 0;

        Matcher m = regex.matcher(val);
        if (m.matches()) {
            numBytes = Integer.parseInt(m.group(1));
            char units = m.group(2).charAt(0);
            switch (units) {
                case 'B':
                    numBytes = numBytes * 1;
                    break;
                case 'K':
                    numBytes = numBytes * 1024;
                    break;
                case 'M':
                    numBytes = numBytes * 1024 * 1024;
                    break;
                case 'G':
                    numBytes = numBytes * 1024 * 1024 * 1024;
                    break;
                case 'P':
                    numBytes = numBytes * 1024 * 1024 * 1024 * 1024;
                    break;
                default:
                    throw new ParseException("Unrecognized units");
            }
        }

        return numBytes;
    }


    public static void parseArgs(String[] args) {

        Options options = new Options();

        options.addOption("U",false,"Specify to Upload Data instead of Download");

        Option xferSize = Option.builder("S")
                .argName("totalSize")
                .hasArg(true)
                .desc("Total Size to transfer")
                .build();
        options.addOption(xferSize);

        Option blkSize = Option.builder("B")
                .argName("blockSize")
                .hasArg(true)
                .desc("Block size ")
                .build();
        options.addOption(blkSize);

        Option gte = Option.builder("g")
                .argName("gate")
                .hasArg(true)
                .required(false)
                .build();
        options.addOption(gte);

        Option aport = Option.builder("a")
                .argName("airport")
                .hasArg(true)
                .required(false)
                .build();
        options.addOption(aport);


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
            if (cmd.hasOption("S")) {
                totalSize = cvtBytes(cmd.getOptionValue("S"));


                if (cmd.hasOption("B")) {
                    blockSize = cvtBytes(cmd.getOptionValue("B"));
                }
                else {
                    blockSize = totalSize;
                }
            }

            if (cmd.hasOption("r")) {
                String val = cmd.getOptionValue("r");

                int v = Integer.parseInt(val);
                randMin = Duration.ofMinutes(v);
            }

            if (cmd.hasOption("g")) {
                gate = cmd.getParsedOptionValue("g");
            }

            if (cmd.hasOption("a")) {
                airport = cmd.getParsedOptionValue("a");
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

        String logFilename = String.format("%s-%s_%s.txt",airport,gate,
                                    formatter.format(ZonedDateTime.now()));

        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(logFilename));

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
                ulTest.run(16,1*1024*1024);
            }



            DownloadTest dlTest = new DownloadTest(service, contentDir, bos);

            ZonedDateTime start = ZonedDateTime.now();


            double total = 0;

            if (randMin.compareTo(Duration.ZERO) != 0) {
                total = dlTest.runRandom(randMin);
            }
            else if (totalSize != null) {
                total = dlTest.run(totalSize,blockSize);
            } else {
                    total = dlTest.run();
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
package com.delta.ifec;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static java.lang.Math.min;
import static java.time.LocalDateTime.now;


public class DownloadTest {

    private Drive service = null;
    private List<File>   drvFiles = new ArrayList<>();
    private BufferedOutputStream logger = null;


    public DownloadTest(Drive svc, File folder, BufferedOutputStream bos) throws IOException {
        service = svc;
        logger = bos;

        String tmp = folder.get("id").toString();

        String qry = String.format("'%s' in parents",tmp);

        FileList top = service.files().list().setQ(qry).execute();
        for (File f: top.getFiles()) {
            File t = service.files().get(f.getId()).setFields("id,size,name").execute();
            drvFiles.add(t);
        }



        Collections.sort(drvFiles, (p1, p2) -> Long.compare((Long)p1.get("size"), (Long)p2.get("size")));

    }

    public long runRandom(Duration howLong) throws IOException {

        Random rand = new Random();
        Duration   cumDuration = Duration.ZERO;
        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.plus(howLong);
        long totalSize = 0;

        while (end.isAfter(LocalDateTime.now())) {
            int ndx = rand.nextInt(0, drvFiles.size());
            File file = drvFiles.get(ndx);

            totalSize += downloadFile(file);

        }

        return totalSize;
    }

    private long downloadFile(File f) throws IOException {

        java.io.File tmp = java.io.File.createTempFile("blob",".dat");
        FileOutputStream fos = new FileOutputStream(tmp);

        System.out.printf("Downloading %s... ", f.getName());
        System.out.flush();
        LocalDateTime start = LocalDateTime.now();


        service.files().get(f.getId()).executeMediaAndDownloadTo(fos);
        LocalDateTime end = LocalDateTime.now();
        Duration duration = Duration.between(start, end);

        long fsize =  tmp.length();
        System.out.printf("%-8d\n",fsize);


        tmp.delete();

        String log = String.format("%s,%s,%d,%s\n",
                DateTimeFormatter.ISO_DATE_TIME.format(start),f.getName(),fsize,duration.toString());
        logger.write(log.getBytes());

        return fsize;
    }

    private File findClosestFile(Long size) {
        File bestFile = drvFiles.get(0);

        Iterator<File> it = drvFiles.iterator();
        while (it.hasNext() ) {
            File file = it.next();
            Long fs = (Long)file.get("size");
            if (fs == size) return file;
            if (fs > size) return bestFile;
            bestFile = file;
        }

        return bestFile;
    }

    public long run(final long totalSize,final long blockSize) throws IOException {


        long bytesRead = 0;

        while (bytesRead < totalSize) {
            long bytesToRead = min(totalSize - bytesRead,blockSize);
            File f = findClosestFile(bytesToRead);

            bytesRead += downloadFile(f);
        }

        return bytesRead;
    }

    public long run() throws IOException {

        boolean failed = false;

        List<String> fileIds = new ArrayList<String>();

        long bytesRead = 0;
        try {
            for (File f : drvFiles) {
                bytesRead += downloadFile(f);

            }
        }
        catch (Exception e) {
            failed = true;
        }

        return bytesRead;
    }
}



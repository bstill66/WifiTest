package com.delta.ifec;

import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.common.io.CharStreams;

import java.io.*;
import java.util.Collections;
import java.util.Random;
import java.util.stream.IntStream;
import com.google.api.services.drive.model.File;

public class Upload {

    Drive service = null;
    String topDirId = null;

    public Upload(Drive svc,String rootDir) {
        service = svc;
        topDirId = rootDir;
    }

    private java.io.File createFile(int size) throws IOException {
        java.io.File tmp = java.io.File.createTempFile("data", "dat");
        byte[] buffer = new byte[1024];
        Random rand = new Random();

        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            int bytesWritten = 0;

            while (bytesWritten < (size*1024)) {
                for (int ndx = 0; ndx < buffer.length; ndx++) {
                    buffer[ndx] = (byte) rand.nextInt(0, 256);
                }
                fos.write(buffer);
                bytesWritten += buffer.length;
            }
        } catch (IOException ioe) {
            tmp.delete();
            throw ioe;
        }

        return tmp;
    }

    public void run(int minKB, int maxKB) throws IOException {

        for (int kb = minKB; kb <= maxKB; kb = kb * 2) {
            java.io.File f = createFile(kb);

            String fname;

            if (kb < 1024) {
                fname = String.format("%03dKB.dat",kb);
            } else {
                if (kb < (1024 * 1024)) {
                    fname = String.format("%03dMB.dat",kb/(1024));
                } else {
                    fname = String.format("%03dGB.dat",kb/(1024*1024));
                }
            }


            File fileMetadata = new File();
            fileMetadata.setName(fname);

            // File's content.

            // Specify media type and file-path for file.
            FileContent mediaContent = new FileContent("application/octet-stream", f);
            fileMetadata.setParents(Collections.singletonList(topDirId));
            try {
                File file = service.files().create(fileMetadata, mediaContent)
                        .setFields("id,parents")
                        .execute();
                System.out.printf("Uploaded %s %d\n",fname,f.length());

            } catch (IOException e) {
                throw e;
            }

            f.delete();
        }
    }
}

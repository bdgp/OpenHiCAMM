package org.bdgp.OpenHiCAMM;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SqlTool {
    public static void main(String[] args) throws IOException {
        List<String> params = new ArrayList<>();
        String cwd = System.getProperty("user.dir");
        
        // Make sure this is a server directory
        File rcFile = new File(cwd, "sqltool.rc");
        PrintWriter pw = null;
        String defaultDb = null;
        for (Path file : Files.newDirectoryStream(Paths.get(cwd), "*.db.properties")) {
            if (pw == null) pw = new PrintWriter(rcFile.getPath());
            defaultDb = file.toFile().getName().replaceFirst("\\.db\\.properties$", "");
            // load the database and start a server if needed
            Connection c = Connection.get(new File(new File(cwd), defaultDb+".db").getPath(), null, true);
            pw.println(String.format("urlid %s", defaultDb));
            pw.println(String.format("url %s", c.url));
            pw.println(String.format("username %s", c.user));
            pw.println(String.format("password %s", c.pw));
            pw.println();
        }
        if (pw != null) pw.close();

        // add sqltool.rc to the command line arguments for sqltool
        if (pw != null) params.add("--rcFile="+rcFile.getPath());
        if (defaultDb != null) params.add(defaultDb);
        params.addAll(Arrays.asList(args));

        // call sqltool with arguments
        org.hsqldb.cmdline.SqlTool.main(params.toArray(new String[]{}));
    }
}

package org.apache.cocoon.reading;

import java.io.*;

import org.apache.cocoon.ProcessingException;

public class SideEffectResourceReader extends ResourceReader{

    @Override
    public void generate() throws IOException, ProcessingException {
        super.generate();
        String sideEffectURL;
        try {
            sideEffectURL = parameters.getParameter("side-effect-uri");
            org.apache.excalibur.source.Source se_source = resolver.resolveURI(sideEffectURL);
            BufferedReader reader = new BufferedReader(new InputStreamReader(se_source.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (Exception e) {
            getLogger().error("Cannot resolve the URL for side-effect:"+e.toString());
        }
    }

}

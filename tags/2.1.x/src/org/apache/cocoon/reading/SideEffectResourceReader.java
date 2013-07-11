package org.apache.cocoon.reading;

import java.io.IOException;

import org.apache.avalon.framework.parameters.ParameterException;
import org.apache.cocoon.ProcessingException;

public class SideEffectResourceReader extends ResourceReader{

    @Override
    public void generate() throws IOException, ProcessingException {
        super.generate();
        String sideEffectURL;
        try {
            sideEffectURL = parameters.getParameter("side-effect-uri");
            resolver.resolveURI(sideEffectURL);
        } catch (Exception e) {
            getLogger().error("Cannot resolve the URL for side-effect:"+e.toString());
        }
    }

}

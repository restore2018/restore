package hk.polyu.comp.jaid.fixer;

import hk.polyu.comp.jaid.fixer.config.Config;

/**
 * Created by Max PEI.
 */
public class Session {

    private static Session session;

    public static void initSession(Config config){
        if(session != null)
            throw new IllegalStateException();

        try {
            session = new Session(config);
        }
        catch(Exception e){
            // Exit.
            throw new IllegalStateException("Failed to initialize the JAID session.");
        }
    }

    public static Session getSession(){
        if(session == null)
            throw new IllegalStateException();

        return session;
    }

    private Session(Config config) throws Exception{
        this.config = config;
    }

    private Config config;

    public Config getConfig() {
        return config;
    }

}

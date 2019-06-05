package hk.polyu.comp.jaid.fixer.log;

/**
 * Created by Max PEI.
 */
public enum LogLevel{
    OFF(0), ERROR(1), WARN(2), INFO(3), DEBUG(4), TRACE(5), ALL(6);

    private int level;

    LogLevel(int level){
        this.level = level;
    }

    public boolean isNotHigherThan(LogLevel logLevel){
        return level <= logLevel.level;
    }
};



package ch.so.agi.gretlgt.logging;

public class CoreJavaLogFactory implements LogFactory {

    private Level globalLogLevel;

    CoreJavaLogFactory(Level globalLogLevel) {
        this.globalLogLevel = globalLogLevel;
    }

    public GretlLogger getLogger(Class logSource) {
        return new CoreJavaLogAdaptor(logSource, globalLogLevel);
    }
}
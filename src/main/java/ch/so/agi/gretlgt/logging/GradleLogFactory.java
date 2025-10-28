package ch.so.agi.gretlgt.logging;

public class GradleLogFactory implements LogFactory {

    GradleLogFactory() {}

    public GretlLogger getLogger(Class logSource) {
        return new GradleLogAdaptor(logSource);
    }
}

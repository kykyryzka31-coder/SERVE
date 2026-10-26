package dev.serve.riseautomation.core;

public interface AutomationTask {
    enum Category { CITY, WORLD, ALLIANCE }
    String id();
    Category category();
    /** Weight 1..3 within a category; all eligible tasks retain a turn. */
    int priority();
    long timeoutMillis();
    boolean enabled();
    boolean validated();
    /** Return verified successful actions only. Zero is allowed; never invent completion. */
    int execute(Cancellation cancellation) throws Exception;
}

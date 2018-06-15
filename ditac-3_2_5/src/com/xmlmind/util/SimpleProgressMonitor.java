/*
 * Copyright (c) 2017 XMLmind Software. All rights reserved.
 *
 * Author: Hussein Shafie
 *
 * This file is part of several XMLmind projects.
 * For conditions of distribution and use, see the accompanying LEGAL.txt file.
 */
package com.xmlmind.util;

/**
 * A simple ProgressMonitor printing error and warning messages 
 * on <code>System.err</code> and other messages (info, verbose, debug) 
 * on <code>System.out</code>.
 * <p>This ProgressMonitor is used to implement command-line utilities.
 */
public final class SimpleProgressMonitor implements ProgressMonitor {
    /**
     * All messages having a type larger than <tt>messageLevel</tt> 
     * are not printed by this monitor.
     */
    public final int messageLevel;

    // -----------------------------------------------------------------------

    /**
     * Equivalent to {@link #SimpleProgressMonitor(Console.MessageType)
     * this(Console.MessageType.INFO)}.
     */
    public SimpleProgressMonitor() {
        this(Console.MessageType.INFO);
    }

    /**
     * Constructs a simple ProgressMonitor.
     *
     * @param messageLevel all messages having a type larger 
     * than <tt>messageLevel</tt> will not be printed by this monitor
     */
    public SimpleProgressMonitor(Console.MessageType messageLevel) {
        this.messageLevel = messageLevel.ordinal();
    }

    public void start() {}

    public boolean message(String message, Console.MessageType messageType) {
        int level = messageType.ordinal();
        if (level <= messageLevel) {
            if (level <= Console.MessageType.WARNING.ordinal()) {
                System.err.println(messageType + ": " + message);
            } else {
                System.out.println(messageType + ": " + message);
            }
        }
        return true;
    }

    public boolean stepCount(int stepCount) {
        return true;
    }

    public boolean step(int step) {
        return true;
    }

    public void stop() {}
}

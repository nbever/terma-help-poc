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
 * A ProgressMonitor which does not report progress. 
 * <p>May be used as a no-op replacement when the ProgressMonitor passed 
 * to a method is <code>null</code>.
 */
public final class NoProgressMonitor implements ProgressMonitor {
    public void start() {}

    public boolean message(String message, Console.MessageType messageType) {
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

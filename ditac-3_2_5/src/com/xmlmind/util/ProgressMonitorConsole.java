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
 * A Console which forwards its message to a {@link ProgressMonitor}.
 */
public final class ProgressMonitorConsole implements Console {
    /**
     * The ProgressMonitor to which messages are forwarded.
     */
    public final ProgressMonitor monitor;

    // -----------------------------------------------------------------------

    /**
     * Constructs a console.
     *
     * @param monitor the ProgressMonitor to which messages are forwarded
     */
    public ProgressMonitorConsole(ProgressMonitor monitor) {
        this.monitor = monitor;
    }

    public void showMessage(String message, MessageType messageType) {
        monitor.message(message, messageType);
    }
}
